(ns net.mynarz.localquiz.qrcode
  (:import [io.nayuki.qrcodegen QrCode QrCode$Ecc]))

(defn url->qrcode-svg
  "Generate a SVG QR code from `url`."
  [^String url
   & {:keys [dark light]}]
  (let [qr (QrCode/encodeText url QrCode$Ecc/MEDIUM)
        qr-size (.size qr)
        path (let [sb (StringBuilder.)]
                (doseq [y (range 0 qr-size)
                        x (range 0 qr-size)]
                  (if (.getModule qr x y)
                    (when (and (not= x 0) (not= y 0)) (.append sb " "))
                    (.append sb (format "M%d,%dh1v1h-1z" x y))))
                (.toString sb))
        dark  (or dark "#1d131dee")
        light (or light "#fbfde3ee")]
    [:svg {:preserveAspectRatio "xMidYMid meet"
           :stroke :none
           :viewBox (format "0 0 %d %d" qr-size qr-size)}
     [:rect {:height :100%
             :width :100%
             :fill dark}]
     [:path {:d path
             :fill light}]]))
