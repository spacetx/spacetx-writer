FAKE ?= image&sizeX=16&sizeY=16&sizeC=2&sizeT=3&sizeZ=4.fake

IMAGE ?= spacetx-fov-writer

SPACETX ?= spacetx/starfish:latest

DIR ?= /tmp/test

all: docker $(FAKE) test verify

docker:
	docker build -t $(IMAGE) .

$(DIR):
	mkdir -m 777 -p $(DIR)

$(FAKE): $(DIR)
	touch "$(DIR)/$(FAKE)"

test: $(FAKE)
	time docker run -t --rm -v $(DIR):/test $(IMAGE) -o /test/out "/test/$(FAKE)"

verify:
	docker pull $(SPACETX)
	docker run --rm -v $(DIR):/test $(SPACETX) validate --experiment-json /test/out/experiment.json

.PHONY: all travis docker test verify
