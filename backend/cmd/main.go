package main

import (
	"goremote/internal/api"
	"goremote/internal/auth"
	"log"
)

func main() {
	tokenStore, err := auth.NewTokenStore(
		"goremote/internal/api/auth/token.hash",
	)
	if err != nil {
		log.Fatal("err")
	}

	pairingManager := auth.NewPairingManager()

	api.HandleRouters(tokenStore, pairingManager)
}
