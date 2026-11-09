package media

type Controller interface {
	PlayPause() error
	Next() error
	Previous() error
}
