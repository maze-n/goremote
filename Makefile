
APP_NAME := GoRemote
APP_DIR := build/$(APP_NAME).app
APP_MACOS := $(APP_DIR)/Contents/MacOS

GOARCH := $(shell uname -m | sed 's/x86_64/amd64/')

.PHONY: app server swift bundle clean

app: server swift bundle

server:
	mkdir -p $(APP_MACOS)
	cd backend && GOOS=darwin GOARCH=$(GOARCH) \
		go build -o ../$(APP_MACOS)/goremote-server ./cmd/

swift:
	cd macos && swift build -c release

bundle:
	mkdir -p $(APP_MACOS)
	mkdir -p $(APP_DIR)/Contents/Resources

	cp macos/.build/release/GoRemote $(APP_MACOS)/GoRemote
	cp macos/Info.plist $(APP_DIR)/Contents/Info.plist

	chmod +x $(APP_MACOS)/*

clean:
	rm -rf build
	cd macos && swift package clean
