(ns net.mynarz.localquiz.question-spec-test
  (:require [net.mynarz.localquiz.spec :as spec]
            [net.mynarz.localquiz.question-spec :as question-spec]
            [clojure.spec.alpha :as s]
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

(deftest hiccup
  (testing "Safe content is accepted"
    (are [content] (s/valid? ::question-spec/hiccup content)
         "plain text"
         [:div [:strong "ok"]]
         [:audio {:src "ok.mp3"}]
         [:a {:href "/safe"} "x"]))
  (testing "Dangerous elements and attributes are rejected"
    (are [content] (not (s/valid? ::question-spec/hiccup content))
         [:script "alert(1)"]
         [:IFRAME {:src "https://evil.com"}]
         [:div [:meta] "Foo"]
         [:img {:src "ok.png" :onerror "alert(1)"}]
         [:div {:data-on:click "alert(1)"} "y"]
         [:a {:href "javascript:alert(1)"} "x"]
         [:a {:href "  JaVaScRiPt:alert(1)"} "x"]))
  (testing "A value-position script in a question's :text is rejected"
    (are [data] (some? (spec/validate ::question-spec/data data))
         {:questions [{:type :yesno :text [:script "alert(1)"] :correct? true}]}))
  (testing "A javascript: string in a non-URL value (e.g. :answer) is left alone"
    (are [data] (nil? (spec/validate ::question-spec/data data))
         {:questions [{:type :open :text "Q" :answer "javascript:foo"}]})))
