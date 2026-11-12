package main

import (
	"context"
	"errors"
	goremotev1 "goremote/gen/go/goremote/v1"
	"goremote/internal/api"
	"goremote/internal/auth"
	management "goremote/internal/grpc/management"
	"goremote/internal/platform/macos"
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
		log.Fatal(err)
	}
	tokenStore, err := auth.NewTokenStore(tokenPath)
	if err != nil {
		log.Fatal("err")
	}

	pairingManager := auth.NewPairingManager()

	// REST API
	httpServer := &http.Server{
		Addr:              ":8090",
		Handler:           api.HandleRoutes(tokenStore, pairingManager),
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
