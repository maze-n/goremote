package media

import (
	"context"
	"fmt"
	"time"

	goremotev1 "goremote/gen/go/goremote/v1"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

type RemoteMediaController struct {
	conn   *grpc.ClientConn
	client goremotev1.MediaControlServiceClient
}

func NewRemoteMediaController(
	target string,
) (*RemoteMediaController, error) {
	conn, err := grpc.NewClient(
		target,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		return nil, fmt.Errorf("create media gRPC client: %w", err)
	}

	client := goremotev1.NewMediaControlServiceClient(conn)

	return &RemoteMediaController{
		conn:   conn,
		client: client,
	}, nil
}

func (m *RemoteMediaController) execute(
	action string,
	call func(
		context.Context,
		*goremotev1.MediaCommandRequest,
		...grpc.CallOption,
	) (*goremotev1.MediaCommandResponse, error),
) error {
	ctx, cancel := context.WithTimeout(
		context.Background(),
		3*time.Second,
	)
	defer cancel()

	response, err := call(
		ctx,
		&goremotev1.MediaCommandRequest{},
	)
	if err != nil {
		return fmt.Errorf(
			"media RPC %q failed: %w",
			action,
			err,
		)
	}

	if !response.Success {
		return fmt.Errorf(
			"media RPC %q rejected: %s",
			action,
			response.Message,
		)
	}

	return nil
}

func (m *RemoteMediaController) PlayPause() error {
	return m.execute("play-pause", m.client.PlayPause)
}

func (m *RemoteMediaController) Next() error {
	return m.execute("next", m.client.Next)
}

func (m *RemoteMediaController) Previous() error {
	return m.execute("previous", m.client.Previous)
}

func (m *RemoteMediaController) Close() error {
	return m.conn.Close()
}
