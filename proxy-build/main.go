package main

import (
	"context"
	"crypto/tls"
	"flag"
	"fmt"
	"log"
	"net"
	"os"
	"os/signal"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/cbeuw/connutil"
	"github.com/google/uuid"
	"github.com/pion/dtls/v3"
	"github.com/pion/dtls/v3/pkg/crypto/selfsign"
	"github.com/pion/logging"
	"github.com/pion/turn/v5"
)

// forceManualCaptcha skips auto captcha solving and always opens manual WebView.
var forceManualCaptcha bool

type getCredsFunc func(string) (string, string, string, error)

type turnParams struct {
	host     string
	port     string
	link     string
	udp      bool
	getCreds getCredsFunc
}

type turnCred struct {
	user, pass, addr string
}

type connectedUDPConn struct {
	*net.UDPConn
}

func (c *connectedUDPConn) WriteTo(p []byte, _ net.Addr) (int, error) {
	return c.Write(p)
}

func poolCreds(f getCredsFunc, poolSize int) getCredsFunc {
	var mu sync.Mutex
	var pool []turnCred
	var cTime time.Time
	var idx int

	return func(link string) (string, string, string, error) {
		mu.Lock()
		defer mu.Unlock()

		if !cTime.IsZero() && time.Since(cTime) > 10*time.Minute {
			pool = nil
			cTime = time.Time{}
		}

		if len(pool) < poolSize {
			u, p, a, err := f(link)
			if err == nil {
				pool = append(pool, turnCred{u, p, a})
				cTime = time.Now()
				log.Printf("Successfully registered User Identity %d/%d", len(pool), poolSize)

				if len(pool) < poolSize {
					time.Sleep(1000 * time.Millisecond)
				}

				c := pool[len(pool)-1]
				idx++
				return c.user, c.pass, c.addr, nil
			}

			log.Printf("Failed to get unique TURN identity: %v", err)
			if len(pool) > 0 {
				log.Printf("Falling back to reusing a previous identity...")
				c := pool[idx%len(pool)]
				idx++
				return c.user, c.pass, c.addr, nil
			}
			return "", "", "", err
		}

		c := pool[idx%len(pool)]
		idx++
		return c.user, c.pass, c.addr, nil
	}
}

func oneDtlsConnection(ctx context.Context, peer *net.UDPAddr, listenConn net.PacketConn, connchan chan<- net.PacketConn, okchan chan<- struct{}, c1 chan<- error) {
	var err error
	defer func() { c1 <- err }()
	dtlsctx, dtlscancel := context.WithCancel(ctx)
	defer dtlscancel()

	var conn1, conn2 net.PacketConn
	conn1, conn2 = connutil.AsyncPacketPipe()
	go func() {
		for {
			select {
			case <-dtlsctx.Done():
				return
			case connchan <- conn2:
			}
		}
	}()

	certificate, certErr := selfsign.GenerateSelfSigned()
	if certErr != nil {
		err = fmt.Errorf("generate self-signed cert: %s", certErr)
		return
	}

	config := &dtls.Config{
		Certificates:          []tls.Certificate{certificate},
		InsecureSkipVerify:    true,
		ExtendedMasterSecret:  dtls.RequireExtendedMasterSecret,
		CipherSuites:          []dtls.CipherSuiteID{dtls.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256},
		ConnectionIDGenerator: dtls.OnlySendCIDGenerator(),
	}

	dtlsConn, dtlsErr := dtls.Client(conn1, peer, config)
	if dtlsErr != nil {
		err = fmt.Errorf("failed to connect DTLS: %s", dtlsErr)
		return
	}

	ctx1, cancel := context.WithTimeout(dtlsctx, 30*time.Second)
	defer cancel()
	if hsErr := dtlsConn.HandshakeContext(ctx1); hsErr != nil {
		err = fmt.Errorf("DTLS handshake: %s", hsErr)
		return
	}

	defer func() {
		if closeErr := dtlsConn.Close(); closeErr != nil {
			log.Printf("close DTLS: %s", closeErr)
		}
		log.Println("Closed DTLS connection")
	}()

	log.Println("Established DTLS connection!")
	if okchan != nil {
		select {
		case okchan <- struct{}{}:
		default:
		}
	}
	go func() {
		if okchan == nil {
			return
		}
		for {
			select {
			case <-dtlsctx.Done():
				return
			case okchan <- struct{}{}:
			}
		}
	}()

	wg := sync.WaitGroup{}
	wg.Add(2)
	context.AfterFunc(dtlsctx, func() {
		_ = listenConn.SetDeadline(time.Now())
		_ = dtlsConn.SetDeadline(time.Now())
	})
	var addr atomic.Value

	go func() {
		defer wg.Done()
		defer dtlscancel()
		buf := make([]byte, 1600)
		for {
			select {
			case <-dtlsctx.Done():
				return
			default:
			}
			n, addr1, err1 := listenConn.ReadFrom(buf)
			if err1 != nil {
				return
			}
			addr.Store(addr1)
			if _, err1 = dtlsConn.Write(buf[:n]); err1 != nil {
				return
			}
		}
	}()

	go func() {
		defer wg.Done()
		defer dtlscancel()
		buf := make([]byte, 1600)
		for {
			select {
			case <-dtlsctx.Done():
				return
			default:
			}
			n, err1 := dtlsConn.Read(buf)
			if err1 != nil {
				return
			}
			addr1, ok := addr.Load().(net.Addr)
			if !ok {
				return
			}
			if _, err1 = listenConn.WriteTo(buf[:n], addr1); err1 != nil {
				return
			}
		}
	}()

	wg.Wait()
	_ = listenConn.SetDeadline(time.Time{})
	_ = dtlsConn.SetDeadline(time.Time{})
}

func oneDtlsConnectionLoop(ctx context.Context, peer *net.UDPAddr, listenConnChan <-chan net.PacketConn, connchan chan<- net.PacketConn, okchan chan<- struct{}) {
	for {
		select {
		case <-ctx.Done():
			return
		case listenConn := <-listenConnChan:
			c := make(chan error)
			go oneDtlsConnection(ctx, peer, listenConn, connchan, okchan, c)
			if err := <-c; err != nil {
				log.Printf("%s", err)
			}
		}
	}
}

func oneTurnConnection(ctx context.Context, tp *turnParams, peer *net.UDPAddr, conn2 net.PacketConn, c chan<- error) {
	var err error
	defer func() { c <- err }()

	user, pass, rawAddr, err1 := tp.getCreds(tp.link)
	if err1 != nil {
		err = fmt.Errorf("failed to get TURN credentials: %s", err1)
		return
	}

	urlhost, urlport, err1 := net.SplitHostPort(rawAddr)
	if err1 != nil {
		err = fmt.Errorf("failed to parse TURN server address: %s", err1)
		return
	}
	if tp.host != "" {
		urlhost = tp.host
	}
	if tp.port != "" {
		urlport = tp.port
	}
	turnServerAddr := net.JoinHostPort(urlhost, urlport)
	turnServerUdpAddr, err1 := net.ResolveUDPAddr("udp", turnServerAddr)
	if err1 != nil {
		err = fmt.Errorf("failed to resolve TURN server address: %s", err1)
		return
	}

	var turnConn net.PacketConn
	var d net.Dialer
	ctx1, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()

	if tp.udp {
		conn, err2 := net.DialUDP("udp", nil, turnServerUdpAddr)
		if err2 != nil {
			err = fmt.Errorf("failed to connect to TURN server: %s", err2)
			return
		}
		defer conn.Close()
		turnConn = &connectedUDPConn{conn}
	} else {
		conn, err2 := d.DialContext(ctx1, "tcp", turnServerAddr)
		if err2 != nil {
			err = fmt.Errorf("failed to connect to TURN server: %s", err2)
			return
		}
		defer conn.Close()
		turnConn = turn.NewSTUNConn(conn)
	}

	var addrFamily turn.RequestedAddressFamily
	if peer.IP.To4() != nil {
		addrFamily = turn.RequestedAddressFamilyIPv4
	} else {
		addrFamily = turn.RequestedAddressFamilyIPv6
	}

	cfg := &turn.ClientConfig{
		STUNServerAddr:         turnServerAddr,
		TURNServerAddr:         turnServerAddr,
		Conn:                   turnConn,
		Username:               user,
		Password:               pass,
		RequestedAddressFamily: addrFamily,
		LoggerFactory:          logging.NewDefaultLoggerFactory(),
		Net:                    &androidNet{},
	}

	client, err1 := turn.NewClient(cfg)
	if err1 != nil {
		err = fmt.Errorf("failed to create TURN client: %s", err1)
		return
	}
	defer client.Close()

	if err1 = client.Listen(); err1 != nil {
		err = fmt.Errorf("failed to listen: %s", err1)
		return
	}

	relayConn, err1 := client.Allocate()
	if err1 != nil {
		err = fmt.Errorf("failed to allocate: %s", err1)
		return
	}
	defer relayConn.Close()

	log.Printf("relayed-address=%s", relayConn.LocalAddr().String())

	wg := sync.WaitGroup{}
	wg.Add(2)
	turnctx, turncancel := context.WithCancel(context.Background())
	context.AfterFunc(turnctx, func() {
		_ = relayConn.SetDeadline(time.Now())
		_ = conn2.SetDeadline(time.Now())
	})
	var relayAddr atomic.Value

	go func() {
		defer wg.Done()
		defer turncancel()
		buf := make([]byte, 1600)
		for {
			select {
			case <-turnctx.Done():
				return
			default:
			}
			n, addr1, err1 := conn2.ReadFrom(buf)
			if err1 != nil {
				return
			}
			relayAddr.Store(addr1)
			if _, err1 = relayConn.WriteTo(buf[:n], peer); err1 != nil {
				return
			}
		}
	}()

	go func() {
		defer wg.Done()
		defer turncancel()
		buf := make([]byte, 1600)
		for {
			select {
			case <-turnctx.Done():
				return
			default:
			}
			n, _, err1 := relayConn.ReadFrom(buf)
			if err1 != nil {
				return
			}
			addr1, ok := relayAddr.Load().(net.Addr)
			if !ok {
				return
			}
			if _, err1 = conn2.WriteTo(buf[:n], addr1); err1 != nil {
				return
			}
		}
	}()

	wg.Wait()
	_ = relayConn.SetDeadline(time.Time{})
	_ = conn2.SetDeadline(time.Time{})
}

func oneTurnConnectionLoop(ctx context.Context, tp *turnParams, peer *net.UDPAddr, connchan <-chan net.PacketConn, t <-chan time.Time) {
	for {
		select {
		case <-ctx.Done():
			return
		case conn2 := <-connchan:
			select {
			case <-t:
				c := make(chan error)
				go oneTurnConnection(ctx, tp, peer, conn2, c)
				if err := <-c; err != nil {
					log.Printf("%s", err)
				}
			default:
			}
		}
	}
}

func getCreds(link string) (string, string, string, error) {
	profile := getRandomProfile()
	name := generateName()

	log.Printf("[VK Auth] Connecting Identity - Name: %s | User-Agent: %s", name, profile.UserAgent)

	resp, err := vkPost("https://login.vk.ru/?act=get_anonym_token",
		"client_id=6287487&token_type=messages&client_secret=QbYic1K3lEV5kTGiqlq2&version=1&app_id=6287487",
		profile)
	if err != nil {
		return "", "", "", fmt.Errorf("anonym token: %s", err)
	}
	token1 := resp["data"].(map[string]interface{})["access_token"].(string)

	escapedName := strings.Replace(strings.Replace(name, " ", "%20", -1), "+", "%2B", -1)
	data := fmt.Sprintf("vk_join_link=https://vk.com/call/join/%s&name=%s&access_token=%s", link, escapedName, token1)
	reqURL := "https://api.vk.ru/method/calls.getAnonymousToken?v=5.274&client_id=6287487"

	const maxCaptchaAttempts = 3
	var token2 string

	for attempt := 0; attempt <= maxCaptchaAttempts; attempt++ {
		resp, err = vkPost(reqURL, data, profile)
		if err != nil {
			return "", "", "", fmt.Errorf("calls token: %s", err)
		}

		if errObj, hasErr := resp["error"].(map[string]interface{}); hasErr {
			errCode, _ := errObj["error_code"].(float64)
			if errCode == 14 {
				if attempt == maxCaptchaAttempts {
					return "", "", "", fmt.Errorf("captcha failed after %d attempts", maxCaptchaAttempts)
				}

				captchaErr := ParseVkCaptchaError(errObj)
				if captchaErr.IsCaptchaError() {
					log.Printf("[Captcha] Attempt %d/%d: solving...", attempt+1, maxCaptchaAttempts)

					var successToken string
					var solveErr error

					if forceManualCaptcha {
						log.Println("[Captcha] Manual mode forced, opening WebView...")
						successToken, solveErr = solveCaptchaViaProxy(captchaErr.RedirectUri)
					} else {
						// Try auto captcha first (TurnBridge approach)
						successToken, solveErr = solveVkCaptcha(context.Background(), captchaErr)
						if solveErr != nil {
							log.Printf("[Captcha] auto captcha failed (attempt %d): %v", attempt+1, solveErr)
							log.Println("[Captcha] Falling back to manual captcha...")
							successToken, solveErr = solveCaptchaViaProxy(captchaErr.RedirectUri)
						}
					}

					if solveErr != nil {
						log.Printf("[Captcha] manual captcha failed (attempt %d): %v", attempt+1, solveErr)
						log.Println("[FATAL] 0 connected streams and manual captcha failed/timed out.")
						log.Println("Fatal manual captcha error. Shutting down application.")
						return "", "", "", fmt.Errorf("FATAL_CAPTCHA_FAILED_NO_STREAMS")
					}

					// Unified success log so Android UI can react in both
					// auto and manual paths (CaptchaSolved event).
					if successToken != "" {
						log.Println("[Captcha] Success! Got success_token")
					}

					if captchaErr.CaptchaAttempt == "0" || captchaErr.CaptchaAttempt == "" {
						captchaErr.CaptchaAttempt = "1"
					}

					data = fmt.Sprintf("vk_join_link=https://vk.com/call/join/%s&name=%s"+
						"&captcha_key=&captcha_sid=%s&is_sound_captcha=0&success_token=%s"+
						"&captcha_ts=%s&captcha_attempt=%s&access_token=%s",
						link, escapedName, captchaErr.CaptchaSid, successToken,
						captchaErr.CaptchaTs, captchaErr.CaptchaAttempt, token1)
					continue
				}
			}
			return "", "", "", fmt.Errorf("VK API error: %v", errObj)
		}

		token2 = resp["response"].(map[string]interface{})["token"].(string)
		break
	}

	okData := fmt.Sprintf("session_data=%%7B%%22version%%22%%3A2%%2C%%22device_id%%22%%3A%%22%s%%22%%2C%%22client_version%%22%%3A1.1%%2C%%22client_type%%22%%3A%%22SDK_JS%%22%%7D&method=auth.anonymLogin&format=JSON&application_key=CGMMEJLGDIHBABABA", uuid.New())
	resp, err = vkPost("https://calls.okcdn.ru/fb.do", okData, profile)
	if err != nil {
		return "", "", "", fmt.Errorf("ok auth: %s", err)
	}
	token3 := resp["session_key"].(string)

	joinData := fmt.Sprintf("joinLink=%s&isVideo=false&protocolVersion=5&anonymToken=%s&method=vchat.joinConversationByLink&format=JSON&application_key=CGMMEJLGDIHBABABA&session_key=%s", link, token2, token3)
	resp, err = vkPost("https://calls.okcdn.ru/fb.do", joinData, profile)
	if err != nil {
		return "", "", "", fmt.Errorf("join call: %s", err)
	}

	turnServer := resp["turn_server"].(map[string]interface{})
	user := turnServer["username"].(string)
	pass := turnServer["credential"].(string)
	turnURL := turnServer["urls"].([]interface{})[0].(string)
	clean := strings.Split(turnURL, "?")[0]
	address := strings.TrimPrefix(strings.TrimPrefix(clean, "turn:"), "turns:")

	return user, pass, address, nil
}

func initAndroidCerts() {
	// Go's crypto/x509 can't find Android's CA certs automatically.
	// Set SSL_CERT_DIR to the standard Android system CA path.
	if os.Getenv("SSL_CERT_FILE") == "" && os.Getenv("SSL_CERT_DIR") == "" {
		for _, dir := range []string{
			"/system/etc/security/cacerts",
			"/etc/security/cacerts",
			"/apex/com.android.conscrypt/cacerts",
		} {
			if _, err := os.Stat(dir); err == nil {
				os.Setenv("SSL_CERT_DIR", dir)
				log.Printf("Using CA certs from %s", dir)
				break
			}
		}
	}
}

func main() {
	initAndroidCerts()

	peerAddr := flag.String("peer", "", "Peer address (IP:PORT)")
	vkLink := flag.String("vk-link", "", "VK call join link or ID")
	listenAddr := flag.String("listen", "127.0.0.1:9000", "Local listen address")
	n := flag.Int("n", 4, "Number of TURN connections")
	manualCaptcha := flag.Bool("manual-captcha", false, "Skip auto captcha, always solve manually")
	dnsFlag := flag.String("dns", "", "Comma-separated carrier/system DNS servers (IP:PORT), tried before public DNS")
	flag.Parse()

	forceManualCaptcha = *manualCaptcha

	if *dnsFlag != "" {
		for _, s := range strings.Split(*dnsFlag, ",") {
			s = strings.TrimSpace(s)
			if s == "" {
				continue
			}
			if !strings.Contains(s, ":") {
				s = s + ":53"
			}
			systemDnsServers = append(systemDnsServers, s)
		}
		if len(systemDnsServers) > 0 {
			log.Printf("Using carrier DNS servers: %v", systemDnsServers)
		}
	}

	if *peerAddr == "" || *vkLink == "" {
		fmt.Fprintln(os.Stderr, "Usage: turngate-proxy -peer IP:PORT -vk-link LINK [-listen ADDR] [-n NUM]")
		os.Exit(1)
	}

	link := *vkLink
	if idx := strings.LastIndex(link, "join/"); idx >= 0 {
		link = link[idx+5:]
	}
	if idx := strings.IndexAny(link, "/?#"); idx != -1 {
		link = link[:idx]
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		<-sigCh
		log.Println("Shutting down...")
		cancel()
	}()

	peer, err := net.ResolveUDPAddr("udp", *peerAddr)
	if err != nil {
		log.Fatalf("Resolve peer: %v", err)
	}

	params := &turnParams{
		host:     "",
		port:     "19302",
		link:     link,
		udp:      true,
		getCreds: poolCreds(getCreds, *n),
	}

	listenConn, err := net.ListenPacket("udp", *listenAddr)
	if err != nil {
		log.Fatalf("Listen: %v", err)
	}
	context.AfterFunc(ctx, func() {
		_ = listenConn.Close()
	})

	listenConnChan := make(chan net.PacketConn)
	go func() {
		for {
			select {
			case <-ctx.Done():
				return
			case listenConnChan <- listenConn:
			}
		}
	}()

	wg := sync.WaitGroup{}
	t := time.Tick(200 * time.Millisecond)
	okchan := make(chan struct{})
	connchan := make(chan net.PacketConn)

	wg.Add(2)
	go func() {
		defer wg.Done()
		oneDtlsConnectionLoop(ctx, peer, listenConnChan, connchan, okchan)
	}()
	go func() {
		defer wg.Done()
		oneTurnConnectionLoop(ctx, params, peer, connchan, t)
	}()

	select {
	case <-okchan:
		log.Printf("Proxy started on %s", *listenAddr)
	case <-ctx.Done():
		return
	}

	for i := 0; i < *n-1; i++ {
		cChan := make(chan net.PacketConn)
		wg.Add(2)
		go func() {
			defer wg.Done()
			oneDtlsConnectionLoop(ctx, peer, listenConnChan, cChan, nil)
		}()
		go func() {
			defer wg.Done()
			oneTurnConnectionLoop(ctx, params, peer, cChan, t)
		}()
	}

	wg.Wait()
}
