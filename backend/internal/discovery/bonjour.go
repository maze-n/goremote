package discovery

import (
	"fmt"
	"net"
	"os"
	"strings"

	"github.com/grandcat/zeroconf"
)

const (
	serviceName = "GoRemote"
	serviceType = "_goremote._tcp"
	domain      = "local."
)

func Start(port int) (*zeroconf.Server, error) {
	// Explicitly select the Mac's Wi-Fi interface.
	iface, err := net.InterfaceByName("en0")
	if err != nil {
		return nil, fmt.Errorf("find Wi-Fi interface: %w", err)
	}

	// Get IPv4 addresses assigned to the interface.
	addrs, err := iface.Addrs()
	if err != nil {
		return nil, fmt.Errorf("get interface addresses: %w", err)
	}

	var ips []string

	for _, addr := range addrs {
		ipNet, ok := addr.(*net.IPNet)
		if !ok {
			continue
		}

		ip := ipNet.IP.To4()
		if ip == nil {
			continue
		}

		ips = append(ips, ip.String())
	}

	if len(ips) == 0 {
		return nil, fmt.Errorf("no IPv4 address found on %s", iface.Name)
	}

	// Normalize the hostname before RegisterProxy appends ".local.".
	hostname, err := os.Hostname()
	if err != nil {
		return nil, fmt.Errorf("get hostname: %w", err)
	}

	hostname = strings.TrimSuffix(hostname, ".")
	for strings.HasSuffix(hostname, ".local") {
		hostname = strings.TrimSuffix(hostname, ".local")
	}

	server, err := zeroconf.RegisterProxy(
		serviceName,
		serviceType,
		domain,
		port,
		hostname,
		ips,
		[]string{"version=0.1.0"},
		[]net.Interface{*iface},
	)
	if err != nil {
		return nil, fmt.Errorf("register Bonjour service: %w", err)
	}

	return server, nil
}
