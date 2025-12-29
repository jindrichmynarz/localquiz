(ns net.mynarz.localquiz.util-test
  (:require [net.mynarz.localquiz.util :as util]
            [clojure.test :refer [are deftest is]]))

(deftest decimal-format
  (are [number formatted] (= (util/decimal-format number) formatted)
       0 "0"
       1.2 "1.2"
       3.333 "3.33"))

(deftest replace-react-fragments
  (is (= (util/replace-react-fragments [:section
                                        [:<>
                                         [:p "Paragraph 1"]]])
         [:section
          [:div
           [:p "Paragraph 1"]]])))
