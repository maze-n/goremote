package volume

import (
	"fmt"
	"goremote/internal/platform/macos"
	"strconv"
	"strings"
)

type MacOSVolumeController struct{}

func NewMacOSVolumeController() *MacOSVolumeController {
	return &MacOSVolumeController{}
}

func (m *MacOSVolumeController) Get() (int, bool, error) {
	script := `
        set settings to get volume settings
		return (output volume of settings as text) & "," & (output muted of settings as text)
	`

	output, err := macos.RunAppleScript(script)
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

func (m *MacOSVolumeController) Set(level int) error {
	if level < 0 || level > 100 {
		return fmt.Errorf("volume must be between 0 and 100")
	}

	script := fmt.Sprintf(
		"set volume output volume %d",
		level,
	)

	_, err := macos.RunAppleScript(script)

	return err
}

func (m *MacOSVolumeController) SetMuted(muted bool) error {
	script := fmt.Sprintf(
		"set volume output muted %t",
		muted,
	)

	_, err := macos.RunAppleScript(script)
	return err
}
