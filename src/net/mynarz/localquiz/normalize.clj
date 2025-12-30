(ns net.mynarz.localquiz.normalize
  (:require [clojure.string :as string])
  (:import (java.text Normalizer Normalizer$Form)))

(defn remove-punctuation
  "Remove punctuation from `s`."
  [^String s]
  (string/replace s #"[\p{Punct}]" ""))

(defn replace-diacritics
  "Replace diacritical characters in `s` with their ASCII analogues."
  [^String s]
  (-> s
      (Normalizer/normalize Normalizer$Form/NFD)
      (string/replace #"[\p{InCombiningDiacriticalMarks}]" "")))

(def normalize-answer
  "Normalize answer to enable non-exact matching."
  (comp string/trim
        string/lower-case
        remove-punctuation
        replace-diacritics))
