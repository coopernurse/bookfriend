(ns bookfriend.util)

(defmacro dbg[x] `(let [x# ~x] (println '~x "=" x#) x#))

(defn trunc
  ([s max] (trunc s max "..."))
  ([s max suffix]
    (if s
      (if (> (count s) max)
        (str (subs s 0 (- max (count suffix))) suffix)
        s)
      s)))

