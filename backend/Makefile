PROTO_DIR := proto
PROTO_FILE := $(PROTO_DIR)/goremote/v1/management.proto

.PHONY: proto

proto:
	protoc \
		-I $(PROTO_DIR) \
		--go_out=. \
		--go_opt=module=goremote \
		--go-grpc_out=. \
		--go-grpc_opt=module=goremote \
		$(PROTO_FILE)