package macos

import (
	"context"
	"fmt"
	"os/exec"
	"strings"
	"time"
)

func RunAppleScript(script string) (string, error) {
	ctx, cancel := context.WithTimeout(
		context.Background(),
		3*time.Second,
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
