(ns bookfriend.db
  (:require [appengine-magic.services.datastore :as ds]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; util ;;
;;;;;;;;;;

(defn entity-to-map [e]
  (if e (zipmap (keys e) (vals e)) nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; entities ;;
;;;;;;;;;;;;;;

;; id is the string key the user entered
(ds/defentity config-entity [^:key id, value])

;; single book in the system.
;; platform = "kindle" or "nook"
(ds/defentity book-entity [^:key id, platform, author, title, product-url, image-url, not-loanable-count, created, modified])

(ds/defentity user-entity [^:key id, name, email, kindle-email, nook-email, email-opt-out, points, created, modified])

(defn create-book-entity [id platform author title product-url image-url not-loanable-count]
  (book-entity. id platform author title product-url image-url not-loanable-count nil nil))

(defn create-user-entity [id name email kindle-email nook-email email-opt-out]
  (user-entity. id name email kindle-email nook-email email-opt-out 0 nil nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; user ;;
;;;;;;;;;;

(defn get-user [id]
  (ds/retrieve user-entity id))

(defn put-user! [user]
  (let [now (System/currentTimeMillis) ]
    (ds/save! (assoc user :created (or (:created user) now) :modified now))))

(defn create-user-if-new! [id name]
  (let [user (get-user id)]
    (if (not user)
      (put-user! (create-user-entity id name nil nil nil 0)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; config ;;
;;;;;;;;;;;;

(defn get-all-config
  []
  (map entity-to-map (ds/query :kind config-entity :limit 1000)))

(defn get-all-config-as-map
  []
  (apply array-map
    (flatten
      (map
        (fn [nv] (list (:id nv) (:value nv)))
        (get-all-config)))))

(defn put-config
  [key value]
  (ds/save! (config-entity. key value)))

(defn delete-config-by-id
  [key]
  (let [b (ds/retrieve config-entity key)]
    (if b
      (ds/delete! b))))
