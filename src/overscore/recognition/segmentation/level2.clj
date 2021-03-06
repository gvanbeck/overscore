;;; Decompose L1 segments vertically into L2 segments, that can then
;;; be classified.
(ns overscore.recognition.segmentation.level2
  (:use overscore.proj
        overscore.utils
        overscore.preprocessing.rle
        overscore.recognition.segmentation.segment)
  (:import java.awt.image.BufferedImage))

(def stem-factor 4)
(def max-zeros-factor 0.3)

(defn create-level2-segments
  "Create L2 segments for a given L1 segment. Do so by doing a
  y-projection, filtering the note stems (which should be less than
  3/2 n), and looking at the runs spaced by less than 1/3 d pixels."
  [^BufferedImage img segment d n]
  (let [proj (projection img :y
                         :start-x (:start-x segment)
                         :end-x (:end-x segment))
        ;; filter stems
        filtered (map #(if (< % (* stem-factor n))
                         0
                         %) proj)
        zeros (count (take-while zero? proj))]
    (loop [proj (drop zeros filtered)
           cur-start zeros
           cur-pos zeros
           result (transient [])]
      (if (empty? proj)
        (persistent!
         ;; Add the last segment if non-empty
         (if (== cur-start cur-pos)
           result
           (conj! result
                  (->segment (:start-x segment) (:end-x segment)
                             cur-start (if (== cur-pos (.getHeight img))
                                         (dec cur-pos)
                                         cur-pos)))))
        (if (== (first proj) 0)
          ;; Handle zeros
          (let [zeros (count (take-while zero? proj))
                new-pos (+ cur-pos zeros)]
            (if (> zeros (* max-zeros-factor d))
              ;; New segment
              (recur (drop zeros proj) new-pos new-pos
                     (conj! result
                            (->segment (:start-x segment) (:end-x segment)
                                       cur-start cur-pos)))
              ;; Those zeros are considered inside of the segment
              (recur (drop zeros proj) cur-start new-pos result)))
          ;; Handle segment part
          (let [nonzeros (count (take-while #(not (zero? %)) proj))]
            (recur (drop nonzeros proj) cur-start (+ cur-pos nonzeros) result)))))))
