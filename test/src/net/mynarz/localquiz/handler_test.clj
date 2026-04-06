(ns net.mynarz.localquiz.handler-test
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.handler :refer [->handler]]
            [net.mynarz.localquiz.session :as session]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [ring.mock.request :as mock]
            [clojure.java.io :as io])
  (:import (clojure.lang Keyword)
           (java.util Collection)))

(defn add-session
  [request {:keys [csrf sid]}]
  (-> request
      (mock/header "x-csrf-token" csrf)
      (mock/cookie "__Host-sid" sid)))

(defn get-cookie
  [^Collection cookies
   ^String cookie-name]
  (some->> cookies
           (keep (partial session/get-cookie cookie-name))
           first))

(defn get-session
  [{{cookies "Set-Cookie"} :headers}]
  {:csrf (get-cookie cookies "csrf")
   :sid (get-cookie cookies "sid")})

(defn request
  ([^Keyword method
    ^String path]
   (request method path {}))
  ([^Keyword method
    ^String path
    params]
   (-> (mock/request method path params)
       (mock/header "Accept-Encoding" "br"))))

(defn join-player
  [handler
   ^String game-id
   ^String player-name]
  (testing "Join player"
    (let [{:keys [status]
           :as response} (handler (request :get (str "/play/" game-id)))
          session (get-session response)]
      (is (= status 200))
      (is (= (-> (request :post (str "/join/" game-id))
                 (add-session session)
                 (assoc-in [:form-params "player-name"] player-name)
                 handler
                 :status)
             204))
      session)))

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
        {:keys [status]
         :as response} (handler (request :get "/"))
        moderator-session (get-session response)
        game-id (:sid moderator-session)]
    (is (= status 200))
    (testing "Create game"
      (let [questions (-> "questions/questions.edn"
                          io/resource
                          io/file)]
        (is (-> (request :post "/create")
                (add-session moderator-session)
                (mock/multipart-body {"question-file" {:value questions}})
                handler
                :status
                (= 204)))))
    (let [player-1 (join-player handler game-id "Jane")
          answer (fn [session]
                   (-> (request :post (str "/answer/" game-id))
                       (add-session session)
                       (assoc-in [:form-params "answer"] "true")
                       handler))]
      (testing "Question"
        (is (-> (request :post "/question")
                (add-session moderator-session)
                handler
                :status
                (= 204))))
      (testing "Player 1 answers in time"
        (is (-> (answer player-1)
                :status
                (= 204))))
      (testing "Player 2 answers late"
        (Thread/sleep (* 1000 (:question-time-out config)))
        (is ((game/winners game-id)
             (:sid player-1)))))))
