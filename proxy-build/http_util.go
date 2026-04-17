package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"time"
)

// directDialContext resolves host via DoH/TCP/UDP cascade (see doh_resolver.go)
// then dials the resolved IP. Bypasses VPN tunnel's DNS.
func directDialContext(ctx context.Context, network, addr string) (net.Conn, error) {
	host, port, err := net.SplitHostPort(addr)
	if err != nil {
		return nil, err
	}

	if net.ParseIP(host) != nil {
		d := net.Dialer{Timeout: 10 * time.Second}
		return d.DialContext(ctx, network, addr)
	}

	ips, err := resolveHost(ctx, host)
	if err != nil {
		return nil, fmt.Errorf("DNS resolve %s: %w", host, err)
	}

	d := net.Dialer{Timeout: 10 * time.Second}
	var lastErr error
	for _, ip := range ips {
		target := net.JoinHostPort(ip, port)
		conn, err := d.DialContext(ctx, network, target)
		if err == nil {
			return conn, nil
		}
		lastErr = err
	}
	return nil, fmt.Errorf("all IPs for %s failed: %w", host, lastErr)
}

var directTransport = &http.Transport{
	DialContext:         directDialContext,
	MaxIdleConns:        100,
	MaxIdleConnsPerHost: 100,
	IdleConnTimeout:     90 * time.Second,
	TLSHandshakeTimeout: 10 * time.Second,
}

func vkPost(url, data string, profile Profile) (map[string]interface{}, error) {
	client := &http.Client{
		Timeout:   20 * time.Second,
		Transport: directTransport,
	}

	req, err := http.NewRequest("POST", url, bytes.NewBufferString(data))
	if err != nil {
		return nil, err
	}

	req.Header.Set("User-Agent", profile.UserAgent)
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	var result map[string]interface{}
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("JSON decode error: %w (body: %s)", err, string(body[:min(len(body), 200)]))
	}

	return result, nil
}

// ctx wrapper for vkPost (uses background internally via http.Client).
var _ = context.Background

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
