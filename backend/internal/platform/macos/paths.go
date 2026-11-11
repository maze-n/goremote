package macos

import (
	"os"
	"path/filepath"
)

func AuthTokenPath() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}

	return filepath.Join(
		home,
		"Library",
		"Application Support",
		"GoRemote",
		"auth",
		"token.hash",
	), nil
}
