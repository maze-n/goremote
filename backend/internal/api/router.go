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

func getVolume(w http.ResponseWriter, req *http.Request) {
    status := struct {
        Volume int `json:"volume"`
        Muted bool `json:"muted"`
    }{}

	volume, muted, err := volumeController.Get()
	if err != nil {
        http.Error(w, err.Error(), http.StatusInternalServerError)
        return
    }

	// Volume
    status.Volume = volume
    status.Muted = muted

    json.NewEncoder(w).Encode(status)
}

func setVolume(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 64)

	var req struct {
		Level int `json:"level"`
	}
	err := json.NewDecoder(r.Body).Decode(&req)
	if err != nil {
		http.Error(w, "Invalid JSON structure", http.StatusBadRequest)
		return
	}

	levelValue := req.Level
	if levelValue < 0 || levelValue > 100 {
		http.Error(w, "volume level must be between 0 and 100", http.StatusBadRequest)
		return
	}

	err = volumeController.Set(levelValue)
	if err != nil {
		http.Error(w, "Something went wrong", http.StatusInternalServerError)
		return
	}

    json.NewEncoder(w).Encode(struct {Status string `json:"status"`}{Status: "ok"})
}

func toggleMute(w http.ResponseWriter, r *http.Request) {
	_, muted, err := volumeController.Get()
	if err != nil {
        http.Error(w, err.Error(), http.StatusInternalServerError)
        return
    }

	err = volumeController.SetMuted(!muted)
	if err != nil {
        http.Error(w, err.Error(), http.StatusInternalServerError)
        return
    }

	json.NewEncoder(w).Encode(struct {Status string `json:"status"`}{Status: "ok"})
}

func HandleRouters() {
    http.HandleFunc("GET /health", JsonContentTypeMiddleware(getHealth))
    http.HandleFunc("GET /status", JsonContentTypeMiddleware(getStatus))
    
	// Volume
	http.HandleFunc("GET /volume", JsonContentTypeMiddleware(getVolume))
	http.HandleFunc("PUT /volume", JsonContentTypeMiddleware(setVolume))
	http.HandleFunc("POST /volume/mute", JsonContentTypeMiddleware(toggleMute))

    log.Println("GoRemote server listening on :8090")

    if err := http.ListenAndServe(":8090", nil); err != nil {
        log.Fatal(err)
    }
}