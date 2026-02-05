(ns net.mynarz.localquiz.session-test
  (:require [net.mynarz.localquiz.session :as session]
            [clojure.test :refer [are deftest]]))

(deftest get-cookie
  (let [cookies "__Host-sid=QgMQ4qK5bwWis7DwgmfytSRUi8U; __Host-csrf=XC7jm_pMoIa3GX73EPEdv1WhTc8s6BkAMw3SQ0FuoxU"]
    (are [cookie-name cookie-value] (= (session/get-cookie cookie-name cookies) cookie-value)
         "sid" "QgMQ4qK5bwWis7DwgmfytSRUi8U"
         "csrf" "XC7jm_pMoIa3GX73EPEdv1WhTc8s6BkAMw3SQ0FuoxU"
         "foo" nil)))
