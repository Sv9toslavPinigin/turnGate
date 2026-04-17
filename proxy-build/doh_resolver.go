package main

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"sync"
	"time"
)

// Mobile carriers frequently block UDP:53 to public DNS (8.8.8.8, 1.1.1.1)
// forcing clients to use carrier DNS. Carrier DNS + active VPN causes the
// system resolver to point at ::1:53 which is unreachable.
//
// This resolver tries, in order:
//   1. DoH over HTTPS:443 directly by IP (443/TCP is almost always open).
//   2. DNS over TCP:53 directly by IP (some carriers block only UDP).
//   3. DNS over UDP:53 directly by IP (original approach).
//
// Results cached in-memory for 5 minutes per host to avoid re-querying
// for every connection attempt.

type dohAnswer struct {
	Name string `json:"name"`
	Type int    `json:"type"`
	TTL  int    `json:"TTL"`
	Data string `json:"data"`
}

type dohResponse struct {
	Status int         `json:"Status"`
	Answer []dohAnswer `json:"Answer"`
}

// dohClient ignores system DNS/proxy and always connects by raw IP.
// Uses SNI from the URL host so TLS cert validation works.
var dohClient = &http.Client{
	Timeout: 6 * time.Second,
	Transport: &http.Transport{
		DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
			d := &net.Dialer{Timeout: 5 * time.Second}
			return d.DialContext(ctx, network, addr)
		},
		TLSClientConfig: &tls.Config{},
		// Short handshake to fail fast on blocked endpoints.
		TLSHandshakeTimeout:   5 * time.Second,
		ResponseHeaderTimeout: 6 * time.Second,
	},
}

type cacheEntry struct {
	ips       []string
	expiresAt time.Time
}

var (
	dnsCacheMu sync.RWMutex
	dnsCache   = map[string]cacheEntry{}
)

// systemDnsServers — DNS операторы/роутера, переданные из Android через -dns флаг.
// Оператор может white-list'ить свой DNS и резать все publics (1.1.1.1, 8.8.8.8).
// Его собственный DNS доступен абонентам всегда.
var systemDnsServers []string

// systemDnsResolver uses DNS servers supplied by Android OS (carrier/router DNS).
// Only created after systemDnsServers is populated from CLI flag.
func makeSystemDnsResolver(proto string) *net.Resolver {
	return &net.Resolver{
		PreferGo: true,
		Dial: func(ctx context.Context, network, address string) (net.Conn, error) {
			d := net.Dialer{Timeout: 4 * time.Second}
			for _, dns := range systemDnsServers {
				conn, err := d.DialContext(ctx, proto, dns)
				if err == nil {
					return conn, nil
				}
			}
			return nil, fmt.Errorf("all system DNS (%s) failed", proto)
		},
	}
}

func cacheGet(host string) ([]string, bool) {
	dnsCacheMu.RLock()
	defer dnsCacheMu.RUnlock()
	e, ok := dnsCache[host]
	if !ok || time.Now().After(e.expiresAt) {
		return nil, false
	}
	return e.ips, true
}

func cachePut(host string, ips []string, ttl time.Duration) {
	if len(ips) == 0 {
		return
	}
	dnsCacheMu.Lock()
	defer dnsCacheMu.Unlock()
	dnsCache[host] = cacheEntry{ips: ips, expiresAt: time.Now().Add(ttl)}
}

// dohLookup resolves host via public DoH endpoints (JSON API).
// Returns A records (IPv4 addresses).
func dohLookup(ctx context.Context, host string) ([]string, error) {
	// Endpoints by IP: carrier usually blocks 8.8.8.8 entirely but 1.1.1.1 survives,
	// and vice versa. We try both. TLS SNI comes from the URL host segment.
	endpoints := []string{
		"https://1.1.1.1/dns-query",
		"https://1.0.0.1/dns-query",
		"https://8.8.8.8/resolve",
		"https://8.8.4.4/resolve",
	}

	var lastErr error
	for _, ep := range endpoints {
		url := fmt.Sprintf("%s?name=%s&type=A", ep, host)
		req, err := http.NewRequestWithContext(ctx, "GET", url, nil)
		if err != nil {
			lastErr = err
			continue
		}
		req.Header.Set("Accept", "application/dns-json")

		resp, err := dohClient.Do(req)
		if err != nil {
			lastErr = err
			continue
		}
		body, _ := io.ReadAll(resp.Body)
		_ = resp.Body.Close()

		if resp.StatusCode != 200 {
			lastErr = fmt.Errorf("DoH %s: HTTP %d", ep, resp.StatusCode)
			continue
		}

		var dr dohResponse
		if err := json.Unmarshal(body, &dr); err != nil {
			lastErr = fmt.Errorf("DoH %s: parse: %w", ep, err)
			continue
		}
		if dr.Status != 0 {
			lastErr = fmt.Errorf("DoH %s: NOERROR status=%d", ep, dr.Status)
			continue
		}

		var ips []string
		for _, a := range dr.Answer {
			if a.Type == 1 && a.Data != "" { // A record
				if ip := net.ParseIP(a.Data); ip != nil && ip.To4() != nil {
					ips = append(ips, a.Data)
				}
			}
		}
		if len(ips) > 0 {
			return ips, nil
		}
	}

	if lastErr == nil {
		lastErr = fmt.Errorf("all DoH endpoints returned no answers")
	}
	return nil, lastErr
}

// Public DNS providers tried on any protocol. Russian operators typically
// whitelist Yandex/SkyDNS/AdGuard.ru because their own infra uses them —
// blocking these would break carrier services.
var publicDnsServers = []string{
	// Yandex DNS (Russian, rarely blocked by RU carriers)
	"77.88.8.8:53",
	"77.88.8.1:53",
	"77.88.8.88:53", // Yandex Safe
	"77.88.8.7:53",  // Yandex Family
	// AdGuard DNS (Russian infra)
	"94.140.14.14:53",
	"94.140.15.15:53",
	// SkyDNS (Russian)
	"193.58.251.251:53",
	// Cloudflare / Google — blocked by some RU carriers but worth trying
	"1.1.1.1:53",
	"1.0.0.1:53",
	"8.8.8.8:53",
	"8.8.4.4:53",
	// Quad9
	"9.9.9.9:53",
}

// tcpDnsResolver uses Go's built-in DNS over TCP.
var tcpDnsResolver = &net.Resolver{
	PreferGo: true,
	Dial: func(ctx context.Context, network, address string) (net.Conn, error) {
		d := net.Dialer{Timeout: 3 * time.Second}
		for _, dns := range publicDnsServers {
			conn, err := d.DialContext(ctx, "tcp", dns)
			if err == nil {
				return conn, nil
			}
		}
		return nil, fmt.Errorf("all TCP DNS endpoints failed")
	},
}

// udpDnsResolver — works on Wi-Fi where UDP:53 is allowed.
var udpDnsResolver = &net.Resolver{
	PreferGo: true,
	Dial: func(ctx context.Context, network, address string) (net.Conn, error) {
		d := net.Dialer{Timeout: 3 * time.Second}
		for _, dns := range publicDnsServers {
			conn, err := d.DialContext(ctx, "udp", dns)
			if err == nil {
				return conn, nil
			}
		}
		return nil, fmt.Errorf("all UDP DNS endpoints failed")
	},
}

// resolveHost tries carrier DNS → DoH → public TCP → public UDP → system.
// Each step logs its outcome so we can diagnose which firewall rules bite.
func resolveHost(ctx context.Context, host string) ([]string, error) {
	if ip := net.ParseIP(host); ip != nil {
		return []string{host}, nil
	}
	if cached, ok := cacheGet(host); ok {
		return cached, nil
	}

	// 1. Carrier DNS over UDP — always works for the ISP's own subscribers.
	if len(systemDnsServers) > 0 {
		sysUdpCtx, cancel := context.WithTimeout(ctx, 4*time.Second)
		ips, err := makeSystemDnsResolver("udp").LookupHost(sysUdpCtx, host)
		cancel()
		if err == nil && len(ips) > 0 {
			log.Printf("[DNS] %s via carrier UDP: %v", host, ips)
			cachePut(host, ips, 5*time.Minute)
			return ips, nil
		}
		log.Printf("[DNS] carrier UDP failed: %v", err)

		// 1b. Carrier DNS over TCP.
		sysTcpCtx, cancel2 := context.WithTimeout(ctx, 4*time.Second)
		ips, err = makeSystemDnsResolver("tcp").LookupHost(sysTcpCtx, host)
		cancel2()
		if err == nil && len(ips) > 0 {
			log.Printf("[DNS] %s via carrier TCP: %v", host, ips)
			cachePut(host, ips, 5*time.Minute)
			return ips, nil
		}
		log.Printf("[DNS] carrier TCP failed: %v", err)
	} else {
		log.Printf("[DNS] no carrier DNS servers provided (-dns flag empty)")
	}

	// 2. DoH via HTTPS:443.
	dohCtx, cancel := context.WithTimeout(ctx, 8*time.Second)
	ips, err := dohLookup(dohCtx, host)
	cancel()
	if err == nil && len(ips) > 0 {
		log.Printf("[DNS] %s via DoH: %v", host, ips)
		cachePut(host, ips, 5*time.Minute)
		return ips, nil
	}
	log.Printf("[DNS] DoH failed: %v", err)

	// 3. Public DNS over TCP:53.
	tcpCtx, cancel3 := context.WithTimeout(ctx, 6*time.Second)
	ips, err = tcpDnsResolver.LookupHost(tcpCtx, host)
	cancel3()
	if err == nil && len(ips) > 0 {
		log.Printf("[DNS] %s via public TCP: %v", host, ips)
		cachePut(host, ips, 5*time.Minute)
		return ips, nil
	}
	log.Printf("[DNS] public TCP failed: %v", err)

	// 4. Public DNS over UDP:53.
	udpCtx, cancel4 := context.WithTimeout(ctx, 5*time.Second)
	ips, err = udpDnsResolver.LookupHost(udpCtx, host)
	cancel4()
	if err == nil && len(ips) > 0 {
		log.Printf("[DNS] %s via public UDP: %v", host, ips)
		cachePut(host, ips, 5*time.Minute)
		return ips, nil
	}
	log.Printf("[DNS] public UDP failed: %v", err)

	// 5. System resolver as last resort.
	sysCtx, cancel5 := context.WithTimeout(ctx, 4*time.Second)
	ips, err = net.DefaultResolver.LookupHost(sysCtx, host)
	cancel5()
	if err == nil && len(ips) > 0 {
		log.Printf("[DNS] %s via system: %v", host, ips)
		cachePut(host, ips, 1*time.Minute)
		return ips, nil
	}
	log.Printf("[DNS] system resolver failed: %v", err)

	return nil, fmt.Errorf("all DNS methods failed for %s", host)
}
