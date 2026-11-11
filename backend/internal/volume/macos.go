package volume

import (
	"context"
	"fmt"
	"os/exec"
	"strconv"
	"strings"
	"time"
)

type MacOSController struct {}

func NewMacOSController() *MacOSController {
	return &MacOSController{}
}

func runAppleScript(script string) (string, error) {
	ctx, cancel := context.WithTimeout(
		context.Background(),
		3 * time.Second,
	)

	defer cancel()

	cmd := exec.CommandContext(
		ctx,
		"/usr/bin/osascript",
		"-e",
		script,
	)

	output, err := cmd.Output()
	if err != nil {
		return "", fmt.Errorf(
			"osascript failed: %w",
			err,
		)
	}

	return strings.TrimSpace(string(output)), nil
} 

func (m *MacOSController) Get() (int, bool, error) {
	script := `
        set settings to get volume settings
		return (output volume of settings as text) & "," & (output muted of settings as text)
	`

	output, err := runAppleScript(script)
	if err != nil {
		return 0, false, err
	}

	parts := strings.Split(output, ",")
	if len(parts) != 2 {
		return 0, false, fmt.Errorf(
			"unexpected volume response: %q",
			output,
		)
	}

	level, err := strconv.Atoi(parts[0])
	if err != nil {
		return 0, false, fmt.Errorf("invalid volume level: %w", err)
	}

	muted, err := strconv.ParseBool(parts[1])
    if err != nil {
        return 0, false, fmt.Errorf("invalid mute state: %w", err)
    }

	return level, muted, nil
}

func (m *MacOSController) Set(val int) error {
	return nil
}

func (m *MacOSController) SetMuted(val bool) error {
	return nil
}