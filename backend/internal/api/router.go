package api

import (
	"encoding/json"
	"net/http"
)


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
		Status string
	}{
		Status: "ok",
	}

	json.NewEncoder(w).Encode(status)
}

func HandleRouters() {
    http.HandleFunc("GET /health", JsonContentTypeMiddleware(getHealth))
    http.HandleFunc("GET /status", JsonContentTypeMiddleware(getStatus))


    http.ListenAndServe(":8090", nil)
}
