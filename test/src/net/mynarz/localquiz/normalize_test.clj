(ns net.mynarz.localquiz.normalize-test
  (:require [net.mynarz.localquiz.normalize :as normalize]
            [clojure.test :refer [are deftest]]))

(deftest remove-punctuation
  (are [original normalized] (= (normalize/remove-punctuation original) normalized)
       "A/B" "AB"
       "U.S.A." "USA"))

(deftest replace-diacritics
  (are [original normalized] (= (normalize/replace-diacritics original) normalized)
       "Příliš žluťoučký kůň úpěl ďábelské ódy" "Prilis zlutoucky kun upel dabelske ody"))

(deftest normalize-answer
  (are [original normalized] (= (normalize/normalize-answer original) normalized)
       "Příliš" "prilis"))
