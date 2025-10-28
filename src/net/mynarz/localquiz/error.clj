(ns net.mynarz.localquiz.error
  (:require [taoensso.timbre :as log]))

(defmacro try-on-error
  [& body]
  `(try
     ~@body
     (catch Throwable ~'t
       (log/error ~'t)
       ;; Return nil when there is an error
       nil)))
