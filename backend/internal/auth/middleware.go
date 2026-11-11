package auth

import (
	"encoding/json"
	"net/http"
	"strings"
)

type TokenVerifier interface {
	Verify(token string) bool
}

func RequireAuth(verifier TokenVerifier) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			header := r.Header.Get("Authorization")

			scheme, token, ok := strings.Cut(header, " ")

			if !ok || !strings.EqualFold(scheme, "Bearer") || token == "" {
				unauthorized(w)
				return
			}

			if !verifier.Verify(token) {
				unauthorized(w)
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}

func unauthorized(w http.ResponseWriter) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusUnauthorized)

	json.NewEncoder(w).Encode(map[string]string{
		"error": "unauthorized",
	})
}
