package media

import (
	"context"
	"fmt"
	"os/exec"
	"time"
)

type MacOSMediaController struct {
	HelperPath string
}

func NewMacOSMediaController(helperPath string) *MacOSMediaController {
	return &MacOSMediaController{
		HelperPath: helperPath,
	}
}

func (m *MacOSMediaController) execute(action string) error {
	ctx, cancel := context.WithTimeout(
		context.Background(),
		3*time.Second,
	)
	defer cancel()

	cmd := exec.CommandContext(ctx, m.HelperPath, action)

	output, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf(
			"media command %q failed: %w: %s",
			action,
			err,
			string(output),
		)
	}

	return nil
}

func (m *MacOSMediaController) PlayPause() error {
	return m.execute("play-pause")
}

func (m *MacOSMediaController) Next() error {
	return m.execute("next")
}

func (m *MacOSMediaController) Previous() error {
	return m.execute("previous")
}
