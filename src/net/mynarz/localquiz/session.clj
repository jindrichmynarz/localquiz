(ns net.mynarz.localquiz.session)

(def a-day
  (* 3600 24))

(defn get-cookie
  "Get the value of `cookie-name` from the `cookies` string."
  [^String cookie-name
   ^String cookies]
  (let [regex (re-pattern (format "__Host-%s=([^;^ ]+)" cookie-name))]
    (try ; In case we get garbage
      (some->> cookies
               (re-find regex)
               second)
      (catch Throwable _))))

(defn get-sid
  "Get session ID from `headers`."
  [headers]
  (get-cookie "sid" (headers "cookie")))

(defn session-cookie
  [^String sid]
  (str "__Host-sid=" sid "; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age=" a-day))

(defn csrf-cookie
  [^String csrf]
  (str "__Host-csrf=" csrf "; Path=/; Secure; SameSite=Lax; Max-Age=" a-day))

(def csrf-cookie-js
  "document.cookie.match(/(^| )__Host-csrf=([^;]+)/)?.[2]")
