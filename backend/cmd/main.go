package main

import (
	"goremote/internal/api"
	"goremote/internal/auth"
	"goremote/internal/platform/macos"
	"log"
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

	api.HandleRouters(tokenStore, pairingManager)
}
