.PHONY: all frontend backend clean lint test update-deps


clean:
	rm -rf target/*


lint:
	clojure -M:dev -m clj-kondo.main --lint src/**


test:
	clojure -M:test -m kaocha.runner


build:
	clojure -M:build


frontend:
	clojure -M:frontend -m shadow.cljs.devtools.cli compile frontend


backend-repl:
	clojure -M:dev:test:repl/cider-refactor


update-deps:
	clojure -X:project/outdated :upgrade true
