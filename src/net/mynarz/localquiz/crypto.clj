(ns net.mynarz.localquiz.crypto
  (:import [java.security MessageDigest]
           [java.security SecureRandom]
           [java.util Base64 Base64$Encoder]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]))

(def ^SecureRandom secure-random
  (SecureRandom/new))

(def ^Base64$Encoder base64-encoder
  (.withoutPadding (Base64/getUrlEncoder)))

(defn bytes->base64
  [^byte/1 b]
  (.encodeToString base64-encoder b))

(defn digest
  "Short digest, compact but with a higher collision rate."
  [data]
  (let [^byte/1 bytes (if (bytes? data)
                        data
                        (String/.getBytes (str data)))]
    (-> (doto (MessageDigest/getInstance "SHA256")
          (MessageDigest/.update bytes))
        (MessageDigest/.digest)
        bytes->base64
        (subs 10))))

(defn hmac-md5
  "Used for a quick stateless CSRF token generation."
  [^SecretKeySpec key-spec
   ^String data]
  (-> (doto (Mac/getInstance "HmacSha256")
        (.init key-spec))
      (.doFinal (String/.getBytes data))
      bytes->base64))

(defn random-unguessable-uid
  "URL-safe base64-encoded 160-bit (20 byte) random value.
  Its execution speed is similar to random-uuid.
  See <https://neilmadden.blog/2018/08/30/moving-away-from-uuids>"
  []
  (let [buffer (byte-array 20)]
    (.nextBytes secure-random buffer)
    (bytes->base64 buffer)))

(defn secret-key->hmac-sha256-keyspec
  [secret-key]
  (SecretKeySpec/new (String/.getBytes secret-key) "HmacSha256"))
