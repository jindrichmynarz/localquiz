(ns net.mynarz.localquiz.handler-test
  (:require [net.mynarz.localquiz.handler :refer [->handler]]
            [net.mynarz.localquiz.session :as session]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [charred.api :as charred]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [ring.mock.request :as mock]
            [starfederation.datastar.clojure.brotli :as brotli]
            [starfederation.datastar.clojure.consts :as consts]
            [taoensso.timbre :as log]
            [clojure.java.io :as io])
  (:import (java.util Collection)))

(defn get-cookie
  [^Collection cookies
   ^String cookie-name]
  (some->> cookies
           (keep (partial session/get-cookie cookie-name))
           first))

(defn request
  [method
   ^String path]
  (-> (mock/request method path)
      (mock/header "Accept-Encoding" "br")))

(use-fixtures :once fixtures/test-db)

(deftest handler-test
  (let [handler (->handler)]
    (testing "GET /"
      (let [{:keys [headers status]} (handler (request :get "/"))]
        (is (= status 200))
        (is (= (headers "Content-Encoding") "br"))))
    (testing "Has to accept Brotli encoding"
      (is (= (-> (mock/request :get "/")
                 handler
                 :status)
             406)))))

(deftest game-test
  (let [handler (->handler)
        {{cookies "Set-Cookie"} :headers} (handler (request :get "/"))
        csrf (get-cookie cookies "csrf")
        sid (get-cookie cookies "sid")
        questions (-> "questions/questions.edn"
                      io/resource
                      io/file)]
    (-> (request :post "/create")
        (mock/header "x-csrf-token" csrf)
        (mock/cookie "__Host-sid" sid)
        (mock/multipart-body {"question-file" {:value questions}})
        handler
        :body
        brotli/decompress
        log/info)))
