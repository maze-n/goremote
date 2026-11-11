package auth

import (
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"sync"
)

type TokenStore struct {
	mu   sync.RWMutex
	path string
	hash [32]byte
	set  bool
}

func NewTokenStore(path string) (*TokenStore, error) {
	store := &TokenStore{path: path}

	data, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		return store, nil
	}
	if err != nil {
		return nil, err
	}

	decoded, err := hex.DecodeString(strings.TrimSpace(string(data)))
	if err != nil || len(decoded) != sha256.Size {
		return nil, errors.New("invalid stored token hash")
	}

	copy(store.hash[:], decoded)
	store.set = true

	return store, nil
}

func GenerateToken() (string, error) {
	b := make([]byte, 32)

	if _, err := rand.Read(b); err != nil {
		return "", err
	}

	return base64.RawURLEncoding.EncodeToString(b), nil
}

func (s *TokenStore) Save(token string) error {
	hash := sha256.Sum256([]byte(token))

	if err := os.MkdirAll(filepath.Dir(s.path), 0700); err != nil {
		return err
	}

	tmp := s.path + ".tmp"

	if err := os.WriteFile(
		tmp,
		[]byte(hex.EncodeToString(hash[:])),
		0600,
	); err != nil {
		return err
	}

	if err := os.Rename(tmp, s.path); err != nil {
		os.Remove(tmp)
		return err
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	s.hash = hash
	s.set = true

	return nil
}

func (s *TokenStore) Verify(token string) bool {
	if token == "" {
		return false
	}

	incoming := sha256.Sum256([]byte(token))

	s.mu.RLock()
	defer s.mu.RUnlock()

	if !s.set {
		return false
	}

	return subtle.ConstantTimeCompare(
		incoming[:],
		s.hash[:],
	) == 1
}
