package volume

type MacOSController struct {}

func NewMacOSController() *MacOSController {
	return &MacOSController{}
}

func (m *MacOSController) Get() (int, bool, error) {
	return 100, true, nil
}

func (m *MacOSController) Set(val int) error {
	return nil
}

func (m *MacOSController) SetMuted(val bool) error {
	return nil
}