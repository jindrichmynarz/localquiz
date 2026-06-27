(ns net.mynarz.localquiz.sanitize
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
            [clojure.math :as m]))

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

(defn event-attr?
  "True if attribute key `k` runs code on an event — a native on* handler or a
  Datastar data-on* handler (case-insensitive)."
  [k]
  (and (or (keyword? k) (string? k))
       (let [attr (string/lower-case (name k))]
         (or (string/starts-with? attr "on")
             (string/starts-with? attr "data-on")))))

(defn sanitize-hiccup
  "Remove dangerous elements and event-handler attributes from `hiccup`."
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
             (remove (comp event-attr? key))
             (into {}))

        :else form))
    hiccup))
