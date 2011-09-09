(ns bookfriend.collections)

;(defn assoc-keys [m1 m2 k]
;  (apply assoc m1 (apply concat (map (fn [x] (list x (x m2))) k))))

(defn create-map-exclude-nil [xs]
  (into {} (remove (comp nil? second) xs)))

(defn map-difference [m1 m2]
  (let [ks1 (set (keys m1))
        ks2 (set (keys m2))
        ks1-ks2 (clojure.set/difference ks1 ks2)
        ks2-ks1 (clojure.set/difference ks2 ks1)
        ks1*ks2 (clojure.set/intersection ks1 ks2)]
    (merge (select-keys m1 ks1-ks2)
           (select-keys m2 ks2-ks1)
           (select-keys m1
                        (remove (fn [k] (= (m1 k) (m2 k)))
                                ks1*ks2)))))
