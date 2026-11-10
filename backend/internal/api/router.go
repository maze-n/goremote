package api

import (
	"encoding/json"
	"goremote/internal/volume"
	"log"
	"net/http"
)

var volumeController volume.Controller = volume.NewMacOSController()


func getHealth(w http.ResponseWriter, req *http.Request) {
    status := struct {
		Status string
	}{
		Status: "ok",
	}

	json.NewEncoder(w).Encode(status)
}

func getStatus(w http.ResponseWriter, req *http.Request) {
    status := struct {
        Volume struct {
            Level int  `json:"level"`
            Muted bool `json:"muted"`
        } `json:"volume"`
    }{}

	volume, muted, err := volumeController.Get()
	if err != nil {
        http.Error(w, err.Error(), http.StatusInternalServerError)
        return
    }

	// Volume
    status.Volume.Level = volume
    status.Volume.Muted = muted

    json.NewEncoder(w).Encode(status)
}

func HandleRouters() {
    http.HandleFunc("GET /health", JsonContentTypeMiddleware(getHealth))
    http.HandleFunc("GET /status", JsonContentTypeMiddleware(getStatus))

    log.Println("GoRemote server listening on :8090")

    if err := http.ListenAndServe(":8090", nil); err != nil {
        log.Fatal(err)
    }
}