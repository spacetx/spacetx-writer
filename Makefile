FAKE ?= primary_image&sizeX=16&sizeY=16&sizeC=2&sizeT=3&sizeZ=4.fake

IMAGE ?= spacetx/spacetx-writer:latest

SPACETX ?= spacetx/starfish:latest

DIR ?= $(PWD)/build/spacetx-writer-test

ID ?= $(shell id -u)

all: docker $(FAKE) test verify

docker:
	docker build -t $(IMAGE) .

$(DIR):
	mkdir -m 777 -p $(DIR)

$(FAKE): $(DIR)
	touch "$(DIR)/$(FAKE)"

test: $(FAKE)
	time docker run -u $(ID) -t --rm -v $(DIR):$(DIR) $(IMAGE) -o $(DIR)/out "$(DIR)/$(FAKE)"

verify:
	docker pull $(SPACETX)
	docker run -u $(ID) --rm -v $(DIR):/data:ro $(SPACETX) validate --experiment-json /data/out/experiment.json

clean:
	rm -rf build

.PHONY: all travis docker test verify clean
