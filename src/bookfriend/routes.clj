(ns bookfriend.routes
  (:use bookfriend.views)
  (:use [bookfriend.util])
  (:use [noir.core :only (defpage pre-route)])
  (:require [bookfriend.db :as db])
  (:require [bookfriend.email :as email])
  (:require [bookfriend.search :as search])
  (:require [bookfriend.requtil :as requtil])
  (:require [bookfriend.collections :as coll])
  (:require [clojure.contrib.json :as json])
  (:require [clj-appengine-oauth.core :as oauth])
  (:require [noir.response :as response])
  (:require [noir.session :as session])
  (:require [noir.validation :as vali])
  (:require [appengine-magic.services.user :as user]))

(pre-route "/secure/*" {}
  (when-not (requtil/logged-in?)
   (response/redirect "/login")))

(pre-route "/admin/*" {}
  (if-not (requtil/logged-in?)
    (response/redirect "/login")
    (if-not (user/user-admin?)
      (response/redirect "/"))))

(defpage "/" {:as req}
  (home-view))

(defpage "/faq" {:as req}
  (faq-view))

(defpage "/search" {:keys [keyword platform]}
  (if (not (empty? keyword))
    (let [kindle? (or (= "both" platform) (= "kindle" platform))
            nook? (or (= "both" platform) (= "nook" platform))
            books (search/search keyword kindle? nook?)]
      (book-list-view "Search Results" (db/merge-book-status books (requtil/get-user)) nil))
    (home-view)))

(defpage "/available" {:keys [next]}
  (let [max-modified (if next (Long/parseLong next) (Long/MAX_VALUE))
        books (time (db/get-available-books (dbg max-modified) 5 (requtil/get-user)))
        ids (dbg (map #(:book-id %) books))
        next-url (if (empty? books) nil (str "/available?next=" (:modified (dbg (last books))))) ]
    (book-list-view "Available Books" books next-url)))

(defpage "/secure/book-status" {:keys [book-id status] }
  (let [user (requtil/get-user)]
    (db/put-book-user (:id user) book-id status "")
    (book-list-cols (db/get-book-with-status book-id user))))

(defpage "/secure/book-cancel" {:keys [book-id] }
  (let [user (requtil/get-user)]
    (db/delete-book-user (:id user) book-id)
    (book-list-cols (db/get-book-with-status book-id user))))

(defpage "/secure/mybooks" {:keys [cancel] }
  (let [user (requtil/get-user)]
    (if cancel (db/delete-book-user (:id user) cancel))
    (mybooks-view (db/get-user-books (:id user)))))

(defpage "/secure/mytasks" {:as req}
  (let [   user (requtil/get-user)
        to-loan (db/get-books-to-loan (:id user))
         to-ack (db/get-loans-to-ack (:id user)) ]
    (mytasks-view to-loan to-ack)))

(defpage "/secure/loan-book" {:keys [book-id]}
  (let [    user (requtil/get-user)
         to-loan (db/get-books-to-loan (:id user))
            book (first (filter #(= book-id (:id %)) to-loan)) ]
    (if book
      (let [ recip (db/get-loan-recip book-id) ]
        (if recip
          (loan-book-view book recip)
          (response/redirect "/secure/mytasks")))
      (response/redirect "/secure/mytasks"))))

(defn loan-book-not-loanable [book-id user-id]
  (let [book (db/get-book book-id) ]
    (db/delete-book-user user-id book-id)
    (if book
      (db/put-book! (assoc book :not-loanable-count (+ 1 (:not-loanable-count book)))))))

(defn loan-book-bad-recip [book-id recip-id]
  (db/delete-book-user recip-id book-id))

(defn loan-book-create [book-id from-user-id to-user-id]
  (let [loan (db/create-loan book-id from-user-id to-user-id)
        book (db/get-book book-id)
        from-user (db/get-user from-user-id)
          to-user (db/get-user to-user-id) ]
    (db/set-loan-id-for-book-user book-id from-user-id (:id loan))
    (db/set-loan-id-for-book-user book-id to-user-id (:id loan))
    (email/send-confirm-loan book from-user to-user)
    (email/send-loan-created book from-user to-user)
    loan))

(defn- loan-ack-private [loan-id user-id success callback]
  (let [loan (db/get-loan loan-id)]
    (if (and loan (= user-id (:to-user-id loan)))
      (let [from-user (db/get-user (:from-user-id loan))
              to-user (db/get-user (:to-user-id loan))
                 book (db/get-book (:book-id loan)) ]
        (db/put-loan! (assoc loan :date-acked (System/currentTimeMillis) :success success))
        (callback book from-user to-user)))))

(defn loan-ack [loan-id user-id]
  (loan-ack-private loan-id user-id true (fn [book from-user to-user]
    (email/loan-ack-success book from-user)
    (db/put-user! (assoc from-user :points (+ 5 (:points from-user))))
    (db/put-user! (assoc to-user :points (+ 2 (:points to-user)))))))

(defn loan-ack-fail [loan-id user-id]
  (loan-ack-private loan-id user-id false (fn [book from-user to-user]
    (email/loan-ack-fail book from-user to-user))))

(defpage "/secure/loan-book-bad-recip" {:keys [book-id recip-id]}
  (loan-book-bad-recip book-id recip-id)
  (session/flash-put! "Sorry about that. This recipient has been removed.")
  (response/redirect (str "/secure/loan-book?book-id=" book-id)))

(defpage "/secure/book-not-loanable" {:keys [book-id]}
  (let [ user (requtil/get-user) ]
    (loan-book-not-loanable book-id (:id user))
    (session/flash-put! "Thanks for letting us know. We've removed this book from your task list")
    (response/redirect "/secure/mytasks")))

(defpage "/secure/loan-book-create" {:keys [book-id recip-id]}
  (let [ user (requtil/get-user) ]
    (loan-book-create book-id (:id user) recip-id)
    (session/flash-put! "Thank you for loaning a book!")
    (response/redirect "/secure/mytasks")))

(defpage "/secure/book-ack-loan" {:keys [loan-id]}
  (let [ user (requtil/get-user) ]
    (loan-ack loan-id (:id user))
    (session/flash-put! "Thanks for acknowledging the loan!")
    (response/redirect "/secure/mytasks")))

(defpage "/secure/book-ack-loan-fail" {:keys [loan-id]}
  (loan-ack-fail loan-id)
  (session/flash-put! "Thanks for letting us know. We have emailed the lender.
     Please see if you can work out the problem together via email.")
  (response/redirect "/secure/mytasks"))

;;;;;;;;;;;;;;;;;;;;;;;
;; settings ;;
;;;;;;;;;;;;;;

(defn optional-email [key val]
  (if (not (empty? val))
    (vali/rule (vali/is-email? val)
      [key "Please enter a valid email address"])))

(defn settings-valid? [{:keys [email nook-email kindle-email]}]
  (optional-email :nook-email nook-email)
  (optional-email :kindle-email kindle-email)
  (vali/rule (vali/is-email? email)
    [:email "Please enter a valid email address"])
  (not (vali/errors? :email :nook-email :kindle-email)))

(defpage [:get "/secure/settings"] {:as req}
  (settings-view (requtil/get-user)))

(defpage [:post "/secure/settings" ] {:as req}
  (if (settings-valid? req)
    (let [u (requtil/get-user)
          req (assoc req :email-opt-out (or (:email-opt-out req) "1")) ]
      (db/put-user! (merge u (select-keys req [:email :nook-email :kindle-email :email-opt-out])))
      (db/prune-user-books-if-email-removed u req)
      (session/flash-put! "Settings Saved")))
  (settings-view req))

;;;;;;;;;;;;;;;;;;;;;;;
;; auth ;;
;;;;;;;;;;

(defn make-oauth-consumer [provider]
  (let [conf (db/get-all-config-as-map)]
    (oauth/make-consumer (conf (str provider ".key")) (conf (str provider ".secret")))))

(defn get-authorize-url [prov-name consumer provider]
  (oauth/get-authorize-url
    provider
    consumer
    (requtil/absolute-url (str "/oauth-callback-" prov-name))))

(defn login-start [prov-name provider & [redirect-url consumer-default]]
  (let [consumer (or consumer-default (make-oauth-consumer prov-name))
             url (or redirect-url (get-authorize-url prov-name consumer provider))]
    (session/put! :oauth-consumer (oauth/debug-consumer consumer))
    (response/redirect url)))

(defn redirect-to-settings []
  (session/flash-put! "Please enter your email address")
  (response/redirect (requtil/absolute-url "/secure/settings")))

(defn set-logged-in-and-redirect
  [user-id name]
  (session/put! :user-id user-id)
  (db/create-user-if-new! user-id name)
  (let [u (db/get-user user-id)]
    (if (empty? (:email u))
      (redirect-to-settings)
      (response/redirect (requtil/absolute-url "/")))))

(defn make-provider-google []
  (oauth/make-provider-google "http://www-opensocial.googleusercontent.com/api/people/"))

(defpage "/login" {:as req}
  (login-view))

(defpage "/logout" {:as req}
  (session/remove! :user-id)
  (response/redirect "/"))

(defpage "/login-admin" {:as req}
  (response/redirect (user/login-url)))

(defpage "/login-twitter" {:as req}
  (login-start "twitter" (oauth/make-provider-twitter)))

(defpage "/login-google" {:as req}
  (login-start "google" (make-provider-google)))

(defpage "/login-facebook" {:as req}
  (let [consumer (make-oauth-consumer "facebook")
        url      (oauth/get-authorize-url-facebook consumer (requtil/absolute-url "/oauth-callback-facebook")) ]
   (login-start "facebook" nil url consumer)))

(defpage "/oauth-callback-twitter" {:as req}
  (let [    consumer (session/get :oauth-consumer)
        access-token (oauth/get-access-token
                       (oauth/make-provider-twitter) consumer (:oauth_verifier req))
           user-json (oauth/get-protected-url
                       consumer
                       access-token
                       "http://api.twitter.com/1/account/verify_credentials.json"
                       "utf-8")
           user-data (json/read-json user-json)]
    (set-logged-in-and-redirect (str "twitter-" (:id user-data)) (:name user-data))))

(defpage "/oauth-callback-google" {:as req}
  (let [    consumer (session/get :oauth-consumer)
        access-token (oauth/get-access-token
                       (make-provider-google) (oauth/debug-consumer consumer) (:oauth_verifier req))
           user-json (oauth/get-protected-url
                       consumer
                       access-token
                       "http://www-opensocial.googleusercontent.com/api/people/@me/@self"
                       "utf-8")
           user-data (json/read-json user-json)]
    (set-logged-in-and-redirect (str "google-" (:id (:entry user-data))) (:displayName (:entry user-data)))))

(defpage "/oauth-callback-facebook" {:keys [code]}
  (let [    consumer (session/get :oauth-consumer)
        access-token (oauth/get-access-token-facebook consumer
                        code (requtil/absolute-url "/oauth-callback-facebook"))
           user-json (oauth/get-protected-url-facebook
                       (:access_token access-token) "https://graph.facebook.com/me" "utf-8")
           user-data (json/read-json user-json)]
    (set-logged-in-and-redirect (str "facebook-" (:id user-data)) (:name user-data))))

