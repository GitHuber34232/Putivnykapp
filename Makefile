SHELL := /bin/bash

.PHONY: generate-ios build-ios-sim build-ios-release-xcframework build-ipa-dispatch

generate-ios:
	./scripts/ios/generate-ios.sh

build-ios-sim:
	./scripts/ios/build-ios-sim.sh

build-ios-release-xcframework:
	./scripts/ios/build-ios-release-xcframework.sh

build-ipa-dispatch:
	./scripts/ios/dispatch-ipa.sh

.PHONY: setup-ipa-ubuntu

setup-ipa-ubuntu:
	./scripts/ios/setup-and-build-ipa-ubuntu.sh