package main

import (
	"goremote/internal/api"
	"goremote/internal/auth"
	"log"
)

func main() {
	tokenStore, err := auth.NewTokenStore(
		"~/Library/Application Support/MacRemote/auth/token.hash",
	)
	if err != nil {
		log.Fatal("err")
	}

	pairingManager := auth.NewPairingManager()

	api.HandleRouters(tokenStore, pairingManager)
}
