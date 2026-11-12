
APP_NAME := GoRemote
APP_DIR := build/$(APP_NAME).app
APP_MACOS := $(APP_DIR)/Contents/MacOS

GOARCH := $(shell uname -m | sed 's/x86_64/amd64/')
MEDIA_HELPER_SRC := backend/internal/platform/macos/media-helper/main.swift
MEDIA_HELPER := backend/bin/media-helper

.PHONY: app server swift media-helper bundle clean

app: bundle

server:
	mkdir -p $(APP_MACOS)
	cd backend && GOOS=darwin GOARCH=$(GOARCH) \
		go build -o ../$(APP_MACOS)/goremote-server ./cmd/

swift:
	cd macos && swift build -c release

media-helper:
	mkdir -p backend/bin
	swiftc $(MEDIA_HELPER_SRC) \
		-o $(MEDIA_HELPER) \
		-framework AppKit \
		-framework CoreGraphics \
		-framework ApplicationServices
	chmod +x $(MEDIA_HELPER)

bundle: server swift media-helper
	mkdir -p $(APP_MACOS)
	mkdir -p $(APP_DIR)/Contents/Resources

	cp macos/.build/release/GoRemote $(APP_MACOS)/GoRemote
	cp macos/Info.plist $(APP_DIR)/Contents/Info.plist
	cp $(MEDIA_HELPER) $(APP_MACOS)/media-helper

	chmod +x $(APP_MACOS)/GoRemote
	chmod +x $(APP_MACOS)/goremote-server
	chmod +x $(APP_MACOS)/media-helper

clean:
	rm -rf build
	cd macos && swift package clean
	rm -f $(MEDIA_HELPER)
