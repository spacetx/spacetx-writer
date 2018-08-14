FAKE ?= image&sizeC=3&sizeT=5&sizeZ=4.fake

travis: docker $(FAKE) test

docker:
	docker build -t sptx .

$(FAKE):
	touch "$(FAKE)"

test: $(FAKE)
	time docker run -t --rm -v /tmp:/tmp -v $(PWD):/src:ro sptx -o /tmp/out "/src/$(FAKE)"

.PHONE: travis docker test
