.PHONY: repl test

repl:
	clj \
		-M:repl:dev \
		--main nrepl.cmdline

test:
	clj \
		-X:test

retest:
	clj \
		-M:dev:test-refresh

build:
	clj \
		-Srepro \
		-T:build \
		uber
