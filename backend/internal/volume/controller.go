package volume

type Controller interface {
	Get() (int, error)
	Set(level int) error
	ToggleMute() error
}
