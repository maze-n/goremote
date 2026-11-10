package volume

type Controller interface {
	Get() (level int, muted bool,err error)
	Set(level int) error
	SetMuted(muted bool) error
}
