package discovery

import "github.com/grandcat/zeroconf"

const (
	serviceName = "GoRemote"
	serviceType = "_goremote._tcp"
	domain      = "local."
)

func Start(port int) (*zeroconf.Server, error) {
	return zeroconf.Register(
		serviceName,
		serviceType,
		domain,
		port,
		[]string{"version=0.1.0"},
		nil,
	)
}
