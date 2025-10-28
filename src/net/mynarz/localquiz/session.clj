(ns net.mynarz.localquiz.session)

(defn get-sid
  "Get session ID from `headers`."
  [headers]
  (try ; In case we get garbage
    (some->> (headers "cookie")
      (re-find #"__Host-sid=([^;^ ]+)")
      second)
    (catch Throwable _)))

(defn session-cookie
  [^String sid]
  (str "__Host-sid=" sid "; Path=/; Secure; HttpOnly; SameSite=Lax"))

(defn csrf-cookie
  [^String csrf]
  (str "__Host-csrf=" csrf "; Path=/; Secure; SameSite=Lax"))

(def csrf-cookie-js
  "document.cookie.match(/(^| )__Host-csrf=([^;]+)/)?.[2]")
