package main

import (
	"context"
	"errors"
	goremotev1 "goremote/gen/go/goremote/v1"
	"goremote/internal/api"
	"goremote/internal/auth"
	"goremote/internal/discovery"
	management "goremote/internal/grpc/management"
	"goremote/internal/media"
	"goremote/internal/platform/macos"
	"goremote/internal/volume"
	"log"
	"net"
	"net/http"
	"os/signal"
	"syscall"
	"time"

	"google.golang.org/grpc"
)

func main() {
	if err := run(); err != nil {
		log.Fatal(err)
	}
}

func run() error {
	tokenPath, err := macos.AuthTokenPath()
	if err != nil {
		return err
	}
	tokenStore, err := auth.NewTokenStore(tokenPath)
	if err != nil {
		return err
	}

	pairingManager := auth.NewPairingManager()

	mediaController, err := media.NewRemoteMediaController("127.0.0.1:8092")
	if err != nil {
		return err
	}

	defer func() {
		if err := mediaController.Close(); err != nil {
			log.Printf("Failed to close media gRPC client: %v", err)
		} else {
			log.Println("Media gRPC client connection closed")
		}
	}()

	volumeController := volume.NewMacOSVolumeController()

	// REST API
	httpServer := &http.Server{
		Addr:              ":8090",
		Handler:           api.HandleRoutes(tokenStore, pairingManager, mediaController, volumeController),
		ReadHeaderTimeout: 5 * time.Second,
	}

	// gRPC Management API
	grpcListener, err := net.Listen("tcp", "127.0.0.1:8091")
	if err != nil {
		return err
	}

	grpcServer := grpc.NewServer()
	managementServer := management.NewManagementServer(pairingManager)
	// Register implementation with gRPC.
	// Connects the generated service definition to server.
	// The generated code handles incoming RPC requests.
	goremotev1.RegisterManagementServiceServer(
		grpcServer,
		managementServer,
	)

	ctx, stop := signal.NotifyContext(
		context.Background(),
		syscall.SIGINT,
		syscall.SIGTERM,
	)
	defer stop()

	serverErrors := make(chan error, 1)

	// Start HTTP Server
	httpListener, err := net.Listen("tcp", httpServer.Addr)
	if err != nil {
		_ = grpcListener.Close()
		return err
	}

	go func() {
		log.Println("GoRemote server listening on :8090")

		if err := httpServer.Serve(httpListener); err != nil &&
			!errors.Is(err, http.ErrServerClosed) {
			serverErrors <- err
		}
	}()

	// Start gRPC server.
	go func() {
		log.Println("gRPC management API listening on 127.0.0.1:8091")

		if err := grpcServer.Serve(grpcListener); err != nil &&
			!errors.Is(err, grpc.ErrServerStopped) {
			serverErrors <- err
		}
	}()

	bonjourServer, err := discovery.Start(8090)
	if err != nil {
		log.Printf("Bonjour advertisement failed: %v", err)
	} else {
		log.Println("Bonjour service advertised: _goremote._tcp")
		defer bonjourServer.Shutdown()
	}

	// Wait for shutdown signal or unexpected server failure.
	var runErr error

	select {
	case <-ctx.Done():
		log.Println("Shutdown signal received")

	case runErr = <-serverErrors:
		log.Printf("Server failed: %v", runErr)
	}

	shutdownCtx, cancel := context.WithTimeout(
		context.Background(),
		5*time.Second,
	)
	defer cancel()

	if err := httpServer.Shutdown(shutdownCtx); err != nil {
		log.Printf("Graceful shutdown failed: %v", err)
		_ = httpServer.Close()
	}

	grpcStopped := make(chan struct{})

	go func() {
		grpcServer.GracefulStop()
		close(grpcStopped)
	}()

	select {
	case <-grpcStopped:
		log.Println("gRPC server stopped")

	case <-shutdownCtx.Done():
		log.Println("gRPC graceful shutdown timed out")
		grpcServer.Stop()
		<-grpcStopped
	}

	log.Println("GoRemote stopped")

	return runErr
}
