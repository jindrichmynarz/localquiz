.PHONY: repl test

repl:
	clojure \
		-M:repl:dev

test:
	clojure \
		-X:dev:test

retest:
	clojure \
		-M:dev:test-refresh

build:
	clojure \
		-Srepro \
		-T:build \
		uber
