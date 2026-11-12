package discovery

import (
	"crypto/rand"
	"fmt"
	"os"
	"path/filepath"
)

func loadOrCreateDeviceId() (string, error) {
	configDir, err := os.UserConfigDir()
	if err != nil {
		return "", fmt.Errorf("get user config directory: %w", err)
	}

	appDir := filepath.Join(configDir, "GoRemote")

	if err := os.MkdirAll(appDir, 0700); err != nil {
		return "", fmt.Errorf("create application directory: %w", err)
	}

	deviceIDPath := filepath.Join(appDir, "device_id")

	data, err := os.ReadFile(deviceIDPath)
	if err == nil {
		deviceID := string(data)

		if !isValidUUID(deviceID) {
			return "", fmt.Errorf("invalid device ID stored at %s", deviceIDPath)
		}

		return deviceID, nil
	}

	if !os.IsNotExist(err) {
		return "", fmt.Errorf("read device ID: %w", err)
	}

	deviceID, err := generateUUID()
	if err != nil {
		return "", fmt.Errorf("generate device ID: %w", err)
	}

	// O_EXCL prevents accidentally overwriting an existing identity.
	file, err := os.OpenFile(
		deviceIDPath,
		os.O_WRONLY|os.O_CREATE|os.O_EXCL,
		0600,
	)
	if err != nil {
		if os.IsExist(err) {
			data, readErr := os.ReadFile(deviceIDPath)
			if readErr != nil {
				return "", fmt.Errorf("read concurrently created device ID: %w", readErr)
			}

			existingID := string(data)
			if !isValidUUID(existingID) {
				return "", fmt.Errorf("invalid device ID stored at %s", deviceIDPath)
			}

			return existingID, nil
		}

		return "", fmt.Errorf("create device ID file: %w", err)
	}

	_, writeErr := file.WriteString(deviceID)
	closeErr := file.Close()

	if writeErr != nil {
		return "", fmt.Errorf("write device ID: %w", writeErr)
	}

	if closeErr != nil {
		return "", fmt.Errorf("close device ID file: %w", closeErr)
	}

	return deviceID, nil
}

func generateUUID() (string, error) {
	var uuid [16]byte

	if _, err := rand.Read(uuid[:]); err != nil {
		return "", err
	}

	// RFC 4122 version 4 UUID.
	uuid[6] = (uuid[6] & 0x0f) | 0x40
	uuid[8] = (uuid[8] & 0x3f) | 0x80

	return fmt.Sprintf(
		"%08x-%04x-%04x-%04x-%012x",
		uuid[0:4],
		uuid[4:6],
		uuid[6:8],
		uuid[8:10],
		uuid[10:16],
	), nil
}

func isValidUUID(value string) bool {
	if len(value) != 36 {
		return false
	}

	for i, char := range value {
		switch i {
		case 8, 13, 18, 23:
			if char != '-' {
				return false
			}
		default:
			if !((char >= '0' && char <= '9') ||
				(char >= 'a' && char <= 'f') ||
				(char >= 'A' && char <= 'F')) {
				return false
			}
		}
	}

	return true
}
