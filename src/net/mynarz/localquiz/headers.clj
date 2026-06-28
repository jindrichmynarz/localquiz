(ns net.mynarz.localquiz.headers
  (:require [clojure.string :as string]))

(def strict-transport
  "Forces https, including on subdomains. Prevents attacker from using
  compromised subdomain."
  "max-age=63072000;includeSubDomains;preload")

(def content-security-policy
  "Defense-in-depth backstop for XSS. Datastar evaluates reactive expressions via
  the Function constructor, so 'unsafe-eval' is required; the policy still forbids
  'unsafe-inline', so injected <script>, on* event handlers and javascript: URLs
  are blocked."
  (string/join "; "
               ["script-src 'self' 'unsafe-eval' https://cdn.jsdelivr.net"
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
