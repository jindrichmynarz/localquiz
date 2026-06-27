(ns net.mynarz.localquiz.sanitize-test
  (:require [net.mynarz.localquiz.sanitize :as sanitize]
            [clojure.test :refer [are deftest]]))

(deftest sanitize-hiccup
  (are [hiccup normalized] (= (sanitize/sanitize-hiccup hiccup) normalized)
       [:script] nil
       [:div [:script {:src "https://evil.com"}] "Foo"] [:div "Foo"]
       {:text [:script "alert(1)"]} {:text nil}
       ;; Other dangerous elements are removed too (case-insensitive, root or nested).
       [:IFRAME {:src "https://evil.com"}] nil
       [:div [:iframe] [:meta] "Foo"] [:div "Foo"]
       ;; Legitimate media is kept, and choice maps don't break the walk.
       {:choices [{:text [:audio]} {:text "B"}]} {:choices [{:text [:audio]} {:text "B"}]}
       ;; Event/Datastar attributes are stripped; other attributes are kept.
       [:img {:src "ok.png" :onerror "alert(1)"}] [:img {:src "ok.png"}]
       [:a {:href "/x" :OnClick "evil()"} "link"] [:a {:href "/x"} "link"]
       [:div {:data-on:click "alert(1)"} "x"] [:div {} "x"]
       ;; javascript: URLs in URL attributes are stripped (whitespace/case tolerant).
       [:a {:href "javascript:alert(1)"} "x"] [:a {} "x"]
       [:a {:href "  JaVaScRiPt:alert(1)"} "x"] [:a {} "x"]
       ;; Safe URLs are kept, and a javascript: string in a non-URL value is left alone.
       [:a {:href "/safe"} "x"] [:a {:href "/safe"} "x"]
       {:answer "javascript:foo"} {:answer "javascript:foo"}))
