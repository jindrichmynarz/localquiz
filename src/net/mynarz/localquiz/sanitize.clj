(ns net.mynarz.localquiz.sanitize
  (:require [clojure.string :as string]
            [clojure.walk :as walk]))

(def disallowed-tags
  "Hiccup element tags removed from question content during sanitization: they can
  run scripts, load external resources, redirect the page, or phish. Media and
  formatting tags (e.g. :audio, :video, :img) are intentionally allowed."
  #{"script" "iframe" "object" "embed" "applet"
    "base" "meta" "link" "style"
    "frame" "frameset" "form" "svg"})

(defn disallowed-element?
  "True if `form` is a Hiccup element whose tag is in `disallowed-tags`
  (case-insensitive, ignoring any #id/.class shorthand)."
  [form]
  (and (vector? form)
       (keyword? (first form))
       (-> form
           first
           name
           string/lower-case
           (string/split #"[#.]")
           first
           disallowed-tags
           boolean)))

(defn disallowed-attr?
  "True if attribute key `k` runs code on an event — a native on* handler or a
  Datastar data-* attribute (case-insensitive)."
  [k]
  (and (or (keyword? k) (string? k))
       (let [attr (string/lower-case (name k))]
         (or (string/starts-with? attr "on")
             (string/starts-with? attr "data")))))

(def url-attrs
  "Attribute names whose value is a URL, where a javascript: scheme would execute."
  #{"href" "src" "action" "formaction" "xlink:href"
    "cite" "poster" "background" "ping" "longdesc"})

(defn url-attr?
  "True if attribute key `k` carries a URL value (case-insensitive)."
  [k]
  (and (or (keyword? k) (string? k))
       (contains? url-attrs (string/lower-case (name k)))))

(defn javascript-url?
  "True if string `v` is a javascript: URL, tolerating leading or embedded
  whitespace, control characters, and case (e.g. \" jAva\\tscript:alert(1)\")."
  [v]
  (and (string? v)
       (-> v
           (string/replace #"[\x00-\x20]+" "")
           string/lower-case
           (string/starts-with? "javascript:"))))

(defn disallowed-attr-entry?
  "True if attribute `[k v]` should be dropped: a disallowed key, or a URL
  attribute carrying a javascript: scheme."
  [[k v]]
  (or (disallowed-attr? k)
      (and (url-attr? k) (javascript-url? v))))

(defn sanitize-hiccup
  "Remove dangerous elements, code-running attributes, and javascript: URLs from `hiccup`."
  [hiccup]
  (walk/postwalk
    (fn [form]
      (cond
        (disallowed-element? form)
        nil

        (and (vector? form) (not (map-entry? form)))
        (->> form
             (remove (some-fn nil? disallowed-element?))
             (into []))

        (map? form)
        (->> form
             (remove disallowed-attr-entry?)
             (into {}))

        :else form))
    hiccup))
