package grpc

import (
	"context"
	goremotev1 "goremote/gen/go/goremote/v1"
	"goremote/internal/auth"
)

const pairingCodeTTLSeconds = 120

type ManagementServer struct {
	goremotev1.UnimplementedManagementServiceServer
	pairingManager *auth.MemoryPairingManager
}

func NewManagementServer(
	pairingManager *auth.MemoryPairingManager,
) *ManagementServer {
	return &ManagementServer{
		pairingManager: pairingManager,
	}
}

func (s *ManagementServer) GetStatus(ctx context.Context, req *goremotev1.GetStatusRequest) (*goremotev1.GetStatusResponse, error) {
	return &goremotev1.GetStatusResponse{
		Running: true,
		Version: "0.1.0",
	}, nil
}

func (s *ManagementServer) StartPairing(ctx context.Context, req *goremotev1.StartPairingRequest) (*goremotev1.StartPairingResponse, error) {
	code, err := s.pairingManager.Generate()
	if err != nil {
		return nil, err
	}

	return &goremotev1.StartPairingResponse{
		Code:             code,
		ExpiresInSeconds: pairingCodeTTLSeconds,
	}, nil
}
