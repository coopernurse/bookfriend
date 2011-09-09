(ns bookfriend.db
  (:require [noir.validation :as vali])
  (:require [appengine-magic.services.datastore :as ds]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; util ;;
;;;;;;;;;;

(defmacro dbg[x] `(let [x# ~x] (println '~x "=" x#) x#))

(defn entity-to-map [e]
  (if e (zipmap (keys e) (vals e)) nil))

(defn array-to-map [xs key]
  (apply array-map (interleave (map #(key %) xs) xs)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; entities ;;
;;;;;;;;;;;;;;

;; id is the string key the user entered
(ds/defentity config-entity [^:key id, value])

;; single book in the system.
;; platform enum: "kindle" "nook"
(ds/defentity book-entity [^:key id, platform, author, title, product-url, image-url, not-loanable-count, created, modified])

(ds/defentity user-entity [^:key id, name, email, kindle-email, nook-email, email-opt-out, points, created, modified])

;; status enum: "want" "have" "dislike"
;; platform enum: "nook" "kindle"
(ds/defentity book-user-entity [^:key id, user-id, book-id, status, loan-id, modified])

(defn create-book-entity [id platform author title product-url image-url not-loanable-count]
  (book-entity. id platform author title product-url image-url not-loanable-count nil nil))

(defn create-user-entity [id name email kindle-email nook-email email-opt-out]
  (user-entity. id name email kindle-email nook-email email-opt-out 0 nil nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; user ;;
;;;;;;;;;;

(defn set-user-platform [u]
  (if u
    (assoc u :platforms (set (filter #(vali/is-email? ((keyword (str % "-email")) u)) ["nook" "kindle"])))
    u))

(defn get-user [id]
  (set-user-platform (ds/retrieve user-entity id)))

(defn put-user! [user]
  (let [now (System/currentTimeMillis) ]
    (ds/save! (assoc user :created (or (:created user) now) :modified now))))

(defn create-user-if-new! [id name]
  (let [user (get-user id)]
    (if (not user)
      (put-user! (create-user-entity id name nil nil nil 0)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; book ;;
;;;;;;;;;;

(defn get-books [book-ids]
  (if (empty? book-ids)
    [ ]
    (ds/query :kind book-entity :filter (:in :id book-ids))))

(defn merge-book [search-book db-book]
  (let [now (System/currentTimeMillis)]
    (if db-book
       ; existing book - merge search result fields into row from db
      (merge db-book
        (select-keys
          (assoc search-book :modified now)
          [:author :title :product-url :image-url :modified]))
      ; book we haven't seen before - create entity from search result
      (book-entity.
        (:id search-book)
        (:platform search-book)
        (:author search-book)
        (:title search-book)
        (:product-url search-book)
        (:image-url search-book) 0 now now))))

(defn get-book-user [user-id book-id]
  (let [id (str user-id "-" book-id) ]
    (ds/retrieve book-user-entity id)))

(defn get-book-status [book-id user]
  {
    :want-count (ds/query :kind book-user-entity :count-only? true :filter [ (= :book-id book-id) (= :status "want")])
    :have-count (ds/query :kind book-user-entity :count-only? true :filter [ (= :book-id book-id) (= :status "have")])
    :user-status (if (nil? user) nil (:status (get-book-user (:id user) book-id)))
  })

(defn get-book-status-map [book-ids user]
  (apply array-map (apply concat (map (fn [id] (list id (bookfriend.db/get-book-status id user))) book-ids))))

(defn merge-book-status [books user]
  (let [         book-ids (map #(:id %) books)
        existing-book-map (array-to-map (get-books book-ids) :id)
          book-status-map (get-book-status-map book-ids user) ]
    (ds/save! (map #(merge-book % (existing-book-map (:id %))) books))
    (map #(merge % (book-status-map (:id %))) books)))

(defn put-book-user [user-id book-id status loan-id]
  (let [now (System/currentTimeMillis)
         id (str user-id "-" book-id) ]
    (ds/save! (book-user-entity. id user-id book-id status loan-id now))))

(defn delete-book-user [user-id book-id]
  (let [id (str user-id "-" book-id)
        bu (ds/retrieve book-user-entity id) ]
    (if bu (ds/delete! bu))))

(defn get-book-with-status [book-id user]
  (first (merge-book-status (get-books [book-id]) user)))

(defn get-user-books [user-id]
  (let [ user-books-map (array-to-map (ds/query :kind book-user-entity :filter (= :user-id user-id) :sort :modified) :book-id)
                  books (get-books (keys user-books-map))
           books-status (map #(assoc % :status (:status (user-books-map (:id %)))) books) ]
    (group-by #(:status %) books-status)))

(defn get-books-to-loan [user-id]
  nil)

(defn get-loans-to-ack [user-id]
  nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; config ;;
;;;;;;;;;;;;

(defn get-all-config []
  (map entity-to-map (ds/query :kind config-entity :limit 1000)))

(defn get-all-config-as-map []
  (apply array-map
    (flatten
      (map
        (fn [nv] (list (:id nv) (:value nv)))
        (get-all-config)))))

(defn put-config [key value]
  (ds/save! (config-entity. key value)))

(defn delete-config-by-id [key]
  (let [b (ds/retrieve config-entity key)]
    (if b
      (ds/delete! b))))
