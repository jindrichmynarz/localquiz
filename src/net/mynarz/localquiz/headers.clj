(ns net.mynarz.localquiz.headers
  (:require [charred.api :as charred]
            [clojure.string :as string])
  (:import (java.nio.charset StandardCharsets)
           (java.security MessageDigest)
           (java.util Base64)))

(def datastar-url
  "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.4/bundles/datastar-rocket.js")

(def importmap
  "Lets modules import the very bundle the page loads, sharing its Datastar.
  Defined here, so that the CSP hash always matches the inline script."
  (charred/write-json-str {:imports {:datastar datastar-url}}))

(defn- csp-hash
  [^String script]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256")
                        (.getBytes script StandardCharsets/UTF_8))]
    (str "'sha256-" (.encodeToString (Base64/getEncoder) digest) "'")))

(def strict-transport
  "Forces https, including on subdomains. Prevents attacker from using
  compromised subdomain."
  "max-age=63072000;includeSubDomains;preload")

(def content-security-policy
  "Defense-in-depth backstop for XSS. Datastar evaluates reactive expressions via
  the Function constructor, so 'unsafe-eval' is required; the policy still forbids
  'unsafe-inline', so injected <script>, on* event handlers and javascript: URLs
  are blocked. The only inline script allowed is the import map, by its hash.
  jsDelivr serves Datastar and SortableJS, including its transitive imports."
  (string/join "; "
               [(str "script-src 'self' https://cdn.jsdelivr.net 'unsafe-eval' "
                     (csp-hash importmap))
                "style-src 'self' 'unsafe-inline'"
                "img-src 'self' https: data:"
                "media-src 'self' https:"
                "base-uri 'none'"
                "object-src 'none'"
                "form-action 'self'"
                "frame-ancestors 'none'"]))

(def default-headers
  {"Content-Type"              "text/html"
   "Strict-Transport-Security" strict-transport
   "Content-Security-Policy"   content-security-policy
   "Referrer-Policy"           "no-referrer"
   "X-Content-Type-Options"    "nosniff"
   "X-Frame-Options"           "deny"
   "Cache-Control"             "no-cache, must-revalidate"})
