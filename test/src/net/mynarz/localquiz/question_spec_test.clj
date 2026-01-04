(ns net.mynarz.localquiz.question-spec-test
  (:require [net.mynarz.localquiz.question-spec :as question-spec]
            [clojure.test :refer [are deftest testing]]))

(deftest url?
  (testing "Valid URLs"
    (are [url] (question-spec/url? url)
         "https://example.com"
         "http://foo.com/bar#baz?q=x"))
  (testing "Invalid URLs"
    (are [not-url] (not (question-spec/url? not-url))
         "bork"
         "#"
         "example.com/foo")))
