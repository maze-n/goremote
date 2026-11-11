package main

import (
	"context"
	"errors"
	"goremote/internal/api"
	"goremote/internal/auth"
	"goremote/internal/platform/macos"
	"log"
	"net/http"
	"os/signal"
	"syscall"
	"time"
)

func main() {
	tokenPath, err := macos.AuthTokenPath()
	if err != nil {
		log.Fatal(err)
	}
	tokenStore, err := auth.NewTokenStore(tokenPath)
	if err != nil {
		log.Fatal("err")
	}

	pairingManager := auth.NewPairingManager()

	handler := api.HandleRoutes(tokenStore, pairingManager)

	server := &http.Server{
		Addr:              ":8090",
		Handler:           handler,
		ReadHeaderTimeout: 5 * time.Second,
	}

	ctx, stop := signal.NotifyContext(
		context.Background(),
		syscall.SIGINT,
		syscall.SIGTERM,
	)
	defer stop()

	go func() {
		log.Println("GoRemote server listening on :8090")

		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Printf("HTTP server error: %v", err)
		}
	}()

	<-ctx.Done()

	log.Println("Shutting down GoRemote...")

	shutdownCtx, cancel := context.WithTimeout(
		context.Background(),
		5*time.Second,
	)
	defer cancel()

	if err := server.Shutdown(shutdownCtx); err != nil {
		log.Printf("Graceful shutdown failed: %v", err)
		_ = server.Close()
	}

	log.Println("GoRemote stopped")
}
