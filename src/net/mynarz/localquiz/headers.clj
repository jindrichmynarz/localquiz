(ns net.mynarz.localquiz.headers)

(def strict-transport
  "Forces https, including on subdomains. Prevents attacker from using
  compromised subdomain."
  "max-age=63072000;includeSubDomains;preload")

(def default-headers
  {"Content-Type"              "text/html"
   "Strict-Transport-Security" strict-transport
   "Referrer-Policy"           "no-referrer"
   "X-Content-Type-Options"    "nosniff"
   "X-Frame-Options"           "deny"
   "Cache-Control"             "no-cache, must-revalidate"})
