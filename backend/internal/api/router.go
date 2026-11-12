package api

import (
	"encoding/json"
	"goremote/internal/auth"
	"goremote/internal/media"
	"goremote/internal/volume"
	"log"
	"net/http"
	"os"
	"path/filepath"
)

func newMediaController() media.Controller {
	executablePath, err := os.Executable()
	if err != nil {
		log.Fatalf("failed to locate GoRemote server executable: %v", err)
	}

	helperPath := filepath.Join(filepath.Dir(executablePath), "media-helper")

	return media.NewMacOSMediaController(helperPath)
}

var mediaController media.Controller = newMediaController()
var volumeController volume.Controller = volume.NewMacOSVolumeController()

func getHealth(w http.ResponseWriter, req *http.Request) {
	status := struct {
		Status string
	}{
		Status: "ok",
	}

	json.NewEncoder(w).Encode(status)
}

func getStatus(w http.ResponseWriter, req *http.Request) {
	w.WriteHeader(http.StatusOK)

	json.NewEncoder(w).Encode(struct {
		Status        string `json:"status"`
		Authenticated bool   `json:"authenticated"`
	}{
		Status:        "ok",
		Authenticated: true,
	})
}

func togglePlayPause(w http.ResponseWriter, req *http.Request) {
	if err := mediaController.PlayPause(); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	json.NewEncoder(w).Encode(struct {
		Status string `json:"status"`
	}{Status: "ok"})
}

func mediaNext(w http.ResponseWriter, req *http.Request) {
	if err := mediaController.Next(); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	json.NewEncoder(w).Encode(struct {
		Status string `json:"status"`
	}{Status: "ok"})
}

func mediaPrevious(w http.ResponseWriter, req *http.Request) {
	if err := mediaController.Previous(); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	json.NewEncoder(w).Encode(struct {
		Status string `json:"status"`
	}{Status: "ok"})
}

func getVolume(w http.ResponseWriter, req *http.Request) {
	status := struct {
		Volume int  `json:"volume"`
		Muted  bool `json:"muted"`
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

	json.NewEncoder(w).Encode(struct {
		Status string `json:"status"`
	}{Status: "ok"})
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

	json.NewEncoder(w).Encode(struct {
		Status string `json:"status"`
	}{Status: "ok"})
}

func HandleRoutes(tokenStore *auth.TokenStore, pairingManager *auth.MemoryPairingManager) http.Handler {
	mux := http.NewServeMux()

	requireAuth := auth.RequireAuth(tokenStore)
	pairHandler := NewPairHandler(tokenStore, pairingManager)

	mux.HandleFunc("GET /health", JsonContentTypeMiddleware(getHealth))

	// Auth
	mux.HandleFunc("POST /pair", JsonContentTypeMiddleware(pairHandler))

	// Status
	mux.Handle("GET /status", requireAuth(JsonContentTypeMiddleware(getStatus)))

	// Media
	mux.Handle("POST /media/play-pause", requireAuth((JsonContentTypeMiddleware(togglePlayPause))))
	mux.Handle("POST /media/next", requireAuth((JsonContentTypeMiddleware(mediaNext))))
	mux.Handle("POST /media/previous", requireAuth((JsonContentTypeMiddleware(mediaPrevious))))

	// Volume
	mux.Handle("GET /volume", requireAuth(JsonContentTypeMiddleware(getVolume)))
	mux.Handle("PUT /volume", requireAuth(JsonContentTypeMiddleware(setVolume)))
	mux.Handle("POST /volume/mute", requireAuth(JsonContentTypeMiddleware(toggleMute)))

	return mux
}
