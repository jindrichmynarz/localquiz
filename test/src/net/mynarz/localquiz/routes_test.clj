(ns net.mynarz.localquiz.routes-test
  (:require [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.routes :as routes]
            [clojure.test :refer [are deftest is]]))

(deftest add-remove-connections
  (let [game-id (crypto/random-unguessable-uid)
        connections {}
        connection-1 :c1
        connection-2 :c2]
    (is (= ((routes/add-connection connections game-id connection-1) game-id)
           #{connection-1}))
    (is (= (-> connections
               (routes/add-connection game-id connection-1)
               (routes/add-connection game-id connection-2))
           {game-id #{connection-1 connection-2}}))
    (is (= (-> connections
               (routes/add-connection game-id connection-1)
               (routes/remove-connection game-id connection-1))
           {}))))
