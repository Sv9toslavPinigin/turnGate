package main

import (
	"context"
	"net"

	transport "github.com/pion/transport/v4"
)

// androidNet implements transport.Net without using netlink (which requires
// root on Android). All networking calls go through standard Go net package,
// but Interfaces() returns a dummy loopback to satisfy pion/turn's requirements.
type androidNet struct{}

func (n *androidNet) ListenPacket(network, address string) (net.PacketConn, error) {
	return net.ListenPacket(network, address)
}

func (n *androidNet) ListenUDP(network string, laddr *net.UDPAddr) (transport.UDPConn, error) {
	return net.ListenUDP(network, laddr)
}

func (n *androidNet) ListenTCP(network string, laddr *net.TCPAddr) (transport.TCPListener, error) {
	l, err := net.ListenTCP(network, laddr)
	if err != nil {
		return nil, err
	}
	return &wrappedTCPListener{l}, nil
}

type wrappedTCPListener struct {
	*net.TCPListener
}

func (l *wrappedTCPListener) AcceptTCP() (transport.TCPConn, error) {
	return l.TCPListener.AcceptTCP()
}

func (n *androidNet) Dial(network, address string) (net.Conn, error) {
	return net.Dial(network, address)
}

func (n *androidNet) DialUDP(network string, laddr, raddr *net.UDPAddr) (transport.UDPConn, error) {
	return net.DialUDP(network, laddr, raddr)
}

func (n *androidNet) DialTCP(network string, laddr, raddr *net.TCPAddr) (transport.TCPConn, error) {
	return net.DialTCP(network, laddr, raddr)
}

func (n *androidNet) ResolveIPAddr(network, address string) (*net.IPAddr, error) {
	return net.ResolveIPAddr(network, address)
}

func (n *androidNet) ResolveUDPAddr(network, address string) (*net.UDPAddr, error) {
	return net.ResolveUDPAddr(network, address)
}

func (n *androidNet) ResolveTCPAddr(network, address string) (*net.TCPAddr, error) {
	return net.ResolveTCPAddr(network, address)
}

func (n *androidNet) Interfaces() ([]*transport.Interface, error) {
	// Return a dummy loopback interface to avoid netlink calls.
	lo := transport.NewInterface(net.Interface{
		Index:        1,
		MTU:          65536,
		Name:         "lo",
		HardwareAddr: nil,
		Flags:        net.FlagUp | net.FlagLoopback,
	})
	lo.AddAddress(&net.IPNet{
		IP:   net.IPv4(127, 0, 0, 1),
		Mask: net.CIDRMask(8, 32),
	})
	return []*transport.Interface{lo}, nil
}

func (n *androidNet) InterfaceByIndex(index int) (*transport.Interface, error) {
	ifaces, _ := n.Interfaces()
	for _, ifc := range ifaces {
		if ifc.Index == index {
			return ifc, nil
		}
	}
	return nil, transport.ErrInterfaceNotFound
}

func (n *androidNet) InterfaceByName(name string) (*transport.Interface, error) {
	ifaces, _ := n.Interfaces()
	for _, ifc := range ifaces {
		if ifc.Name == name {
			return ifc, nil
		}
	}
	return nil, transport.ErrInterfaceNotFound
}

func (n *androidNet) CreateDialer(dialer *net.Dialer) transport.Dialer {
	return dialer
}

func (n *androidNet) CreateListenConfig(config *net.ListenConfig) transport.ListenConfig {
	return &androidListenConfig{config}
}

type androidListenConfig struct {
	*net.ListenConfig
}

func (lc *androidListenConfig) Listen(ctx context.Context, network, address string) (net.Listener, error) {
	return lc.ListenConfig.Listen(ctx, network, address)
}

func (lc *androidListenConfig) ListenPacket(ctx context.Context, network, address string) (net.PacketConn, error) {
	return lc.ListenConfig.ListenPacket(ctx, network, address)
}
