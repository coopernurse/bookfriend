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

(defn is-email? [x]
  (and x (vali/is-email? x)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; entities ;;
;;;;;;;;;;;;;;

(def platforms ["nook" "kindle"])

;; id is the string key the user entered
(ds/defentity config-entity [^:key id, value])

;; single book in the system.
;; platform enum: "kindle" "nook"
(ds/defentity book-entity [^:key id, platform, author, title, product-url, image-url, not-loanable-count, created, modified])

(ds/defentity user-entity [^:key id, name, email, kindle-email, nook-email, email-opt-out, points, created, modified])

;; status enum: "want" "have" "dislike"
;; platform enum: "nook" "kindle"
(ds/defentity book-user-entity [^:key id, user-id, book-id, status, loan-id, modified])

;; success: boolean
;; date-loaned: set when we create the loan record
;; date-acked: set when to-user-id acknowledges that he loan succeeded or failed
;;
(ds/defentity loan-entity [^:key id, book-id, from-user-id, to-user-id, success, date-loaned, date-acked])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-book-entity [id platform author title product-url image-url not-loanable-count]
  (book-entity. id platform author title product-url image-url not-loanable-count 0 0))

(defn create-user-entity [id name email kindle-email nook-email email-opt-out]
  (user-entity. id name email kindle-email nook-email email-opt-out 0 0 0))

(defn create-loan-entity [id book-id from-user-id to-user-id]
  (let [now (System/currentTimeMillis)]
    (loan-entity. id book-id from-user-id to-user-id false now 0)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; user ;;
;;;;;;;;;;

(defn set-user-platform [u]
  (if u
    (assoc u :platforms (set (filter #(is-email? ((keyword (str % "-email")) u)) platforms)))
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

(defn set-user-points [user-id points]
  (let [user (get-user user-id) ]
    (if user
      (put-user! (assoc user :points points)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; book ;;
;;;;;;;;;;

(defn get-book [book-id]
  (ds/retrieve book-entity book-id))

(defn get-books [book-ids]
  (if (empty? book-ids)
    [ ]
    (ds/query :kind book-entity :filter (:in :id book-ids))))

(defn put-book! [book]
  (ds/save! book))

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

(defn put-book-user-save [user book-id status loan-id]
  (let [id (str (:id user) "-" book-id)]
    (ds/save! (book-user-entity. id (:id user) book-id status loan-id (System/currentTimeMillis)))
    (if (< (:points user) 15)
      (put-user! (assoc user :points (+ 1 (:points user)))))))

(defn put-book-user [user-id book-id status loan-id]
  (let [user (get-user user-id)]
    (if user (put-book-user-save user book-id status loan-id))))

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

(defn get-loan-recip [book-id]
  (let [ book-users (ds/query :kind book-user-entity :filter [(= :book-id book-id) (= :loan-id "") (= :status "want")])
           user-ids (map #(:user-id %) book-users) ]
    (if (empty? user-ids)
      []
      (let [ users (sort-by :points > (ds/query :kind user-entity :filter (:in :id user-ids))) ]
        (first users)))
  ))

(defn get-books-to-loan [user-id]
  (let [ have (ds/query :kind book-user-entity
                        :filter [(= :user-id user-id) (= :status "have") (= :loan-id "")])
         have-ids (set (map #(:book-id %) have)) ]
    (if (empty? have-ids)
      [ ]
      (let [want (ds/query :kind book-user-entity
                        :filter [(:in :book-id have-ids) (= :status "want") (= :loan-id "")] )
            want-ids (set (map #(:book-id %) want))]
        (if (empty? want-ids)
          [ ]
          (let [want-by-book-id (group-by :book-id want)
                want-user-ids (map #(:user-id %) want)
                books (get-books want-ids)
                books-with-recips (map #(assoc % :recips (want-by-book-id (:id %))) books) ]
            books-with-recips))))))

(defn get-loans-to-ack [user-id]
  (let [ to-ack (ds/query :kind loan-entity :filter [(= :to-user-id user-id) (= :date-acked 0) ])
         to-ack-map (array-to-map to-ack :book-id) ]
    (if (empty? to-ack)
      []
      (let [books (get-books (keys to-ack-map))
            books-with-loan-id (map #(assoc % :loan-id (:id (to-ack-map (:id %)))) books) ]
        books-with-loan-id))))

(defn platform-removed? [old-user new-user platform]
  (let [kw (keyword (str platform "-email")) ]
    (and (is-email? (kw old-user)) (not (is-email? (kw new-user))))))

(defn removed-platforms [old-user new-user]
  (filter #(platform-removed? old-user new-user %) platforms))

(defn prune-user-books [user-id platform]
  (let [book-user (ds/query :kind book-user-entity :filter [(= :user-id user-id) (= :loan-id "")])
            books (get-books (set (map #(:book-id %) book-user)))
        books-for-platform (filter #(= platform (:platform %)) books) ]
    (doall (map #(delete-book-user user-id (:id %)) books-for-platform))))

(defn prune-user-books-if-email-removed [old-user new-user]
  (doall (map #(prune-user-books (:id old-user) %) (removed-platforms old-user new-user))))

(defn set-loan-id-for-book-user [book-id user-id loan-id]
  (let [book-user (get-book-user user-id book-id)
              now (System/currentTimeMillis)]
    (if book-user
      (ds/save! (assoc book-user :loan-id loan-id :modified now)))))
  
(defn put-loan! [loan]
  (let [now (System/currentTimeMillis) ]
    (ds/save! (assoc loan :created (or (:created loan) now) :modified now))))

(defn create-loan [book-id from-user-id to-user-id]
  (put-loan! (create-loan-entity (.toString (java.util.UUID/randomUUID)) book-id from-user-id to-user-id)))

(defn get-loan [loan-id]
  (ds/retrieve loan-entity loan-id))

(defn assoc-book-to-book-user [book-user book-map]
  (let [book (book-map (:book-id book-user))]
    (assoc book-user :author (:author book) :title (:title book))))

(defn get-recent-book-user [num-books]
  (let [book-users (ds/query :kind book-user-entity :sort [[:modified :desc]] :limit num-books)
          book-ids (set (map #(:book-id %) book-users))
          book-map (array-to-map (get-books book-ids) :id) ]
    (map #(assoc-book-to-book-user % book-map) (dbg book-users))))

(defn get-recent-activity [num-books]
  { :total-users (ds/query :kind user-entity :count-only? true)
    :available-books (ds/query :kind book-user-entity :count-only? true :filter [(= :loan-id "") (= :status "have")])
    :activity (get-recent-book-user num-books) })

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
