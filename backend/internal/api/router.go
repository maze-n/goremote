package api

import (
	"encoding/json"
	"net/http"
)


func health(w http.ResponseWriter, req *http.Request) {
    status := struct {
		Status string
	}{
		Status: "ok",
	}

	json.NewEncoder(w).Encode(status)
}

func HandleRouters() {
    http.HandleFunc("/health", health)


    http.ListenAndServe(":8090", nil)
}
