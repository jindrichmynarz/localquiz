(ns net.mynarz.localquiz.network-test
  (:require [net.mynarz.localquiz.network :as network]
            [clojure.test :refer [deftest is testing]]))

(defn- choices
  "Choices describing the network of `arcs`, each `[parent child]`."
  [arcs]
  (vec (for [[child arcs] (group-by second arcs)]
         {:label child
          :related (mapv first arcs)})))

(def ^:private fans
  "A planar network: the root's children form a path, and each has a fan of grandchildren
  that form a path too."
  (choices (concat (for [i (range 6)]
                     ["root" (str "c" i)])
                   (for [i (range 5)]
                     [(str "c" i) (str "c" (inc i))])
                   (for [i (range 6)
                         j (range 4)]
                     [(str "c" i) (str "g" i "-" j)])
                   (for [i (range 6)
                         j (range 3)]
                     [(str "g" i "-" j) (str "g" i "-" (inc j))]))))

(def ^:private k33
  "A network that is not planar: the root above K3,3."
  (choices (concat (for [i (range 3)]
                     ["root" (str "a" i)])
                   (for [i (range 3)
                         j (range 3)]
                     [(str "a" i) (str "b" j)]))))

(deftest validation
  (testing "A network needs one root"
    (is (network/single-root? fans))
    (is (not (network/single-root? (choices [["a" "b"] ["c" "d"]])))))
  (testing "A network must not have cycles"
    (is (network/acyclic? fans))
    (is (not (network/acyclic? (choices [["root" "a"] ["a" "b"] ["b" "a"]])))))
  (testing "A network is capped in size"
    (is (network/within-max-nodes? fans))
    (is (not (network/within-max-nodes? (choices (for [i (range network/max-nodes)]
                                                   ["root" (str i)])))))))

(defn- edge
  [[a b]]
  (hash-set a b))

(deftest layout
  (testing "A planar network is drawn around its root without crossings"
    (let [positions (network/layout fans)
          arcs      (:arcs (network/network fans))]
      (is (= [0.0 0.0] (positions "root")))
      (is (= (count positions) (count (set (vals positions)))))
      (is (empty? (network/crossings arcs positions)))))
  (testing "Of a network that is not planar, only edges left out of its planar backbone cross"
    (let [positions (network/layout k33)
          {:keys [arcs nodes]} (network/network k33)
          backbone  (set (map edge (#'network/backbone nodes arcs "root")))]
      (is (seq (network/crossings arcs positions)))
      (is (every? (fn [pair] (some (comp not backbone edge) pair))
                  (network/crossings arcs positions)))))
  (testing "A network too small to triangulate"
    (is (= {"root" [0.0 0.0] "a" [100.0 0.0]}
           (network/layout (choices [["root" "a"]]))))))
