(ns net.mynarz.localquiz.question-spec-test
  (:require [net.mynarz.localquiz.spec :as spec]
            [net.mynarz.localquiz.question-spec :as question-spec]
            [clojure.test :refer [are deftest testing]]))

(deftest creators
  (are [data] (nil? (spec/validate ::question-spec/creators data))
       [{:name "Alfred"
         :url "https://alfredo.com"}]))

(deftest http?
  (testing "Valid HTTP URLs"
    (are [url] (question-spec/http? url)
         "http://example.com"
         "https://dot.com"))
  (testing "Invalid HTTP URLs"
    (are [url] (not (question-spec/http? url))
         "ftp://uni.edu"
         "urn:nbn:x-base")))

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
