package api

import (
	"encoding/json"
	"errors"
	"goremote/internal/auth"
	"io"
	"net/http"
	"strings"
)

type PairHandler struct {
	tokenStore     *auth.TokenStore
	pairingManager *auth.MemoryPairingManager
}

func NewPairHandler(
	tokenStore *auth.TokenStore,
	pairingManager *auth.MemoryPairingManager,
) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req struct {
			Code       string `json:"code"`
			DeviceName string `json:"device_name"`
		}

		// Limit request body size.
		r.Body = http.MaxBytesReader(w, r.Body, 4096)

		decoder := json.NewDecoder(r.Body)
		decoder.DisallowUnknownFields()

		if err := decoder.Decode(&req); err != nil {
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}

		// Reject multiple JSON values.
		var extra any
		if err := decoder.Decode(&extra); !errors.Is(err, io.EOF) {
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}

		req.Code = strings.TrimSpace(req.Code)
		req.DeviceName = strings.TrimSpace(req.DeviceName)

		if req.Code == "" || req.DeviceName == "" {
			http.Error(
				w,
				"code and device_name are required",
				http.StatusBadRequest,
			)
			return
		}

		// 1. Validate and consume the one-time pairing PIN.
		if !pairingManager.VerifyAndConsume(req.Code) {
			http.Error(
				w,
				"invalid or expired pairing code",
				http.StatusUnauthorized,
			)
			return
		}

		// 2. Generate a secure access token.
		token, err := auth.GenerateToken()
		if err != nil {
			http.Error(
				w,
				"could not generate access token",
				http.StatusInternalServerError,
			)
			return
		}

		// 3. Persist the token hash on the Mac.
		if err := tokenStore.Save(token); err != nil {
			http.Error(
				w,
				"could not save access token",
				http.StatusInternalServerError,
			)
			return
		}

		// 4. Return the plaintext token to Android.
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)

		json.NewEncoder(w).Encode(map[string]string{
			"access_token": token,
			"token_type":   "Bearer",
		})
	}
}
