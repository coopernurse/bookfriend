(ns bookfriend.routes
  (:use bookfriend.views)
  (:use [noir.core :only (defpage pre-route)])
  (:require [bookfriend.db :as db])
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

(defn optional-email [key val]
  (if (not (empty? val))
    (vali/rule (vali/is-email? val)
      [key "Please enter a valid email address"])))

(defn settings-valid? [{:keys [email nook-email kindle-email]}]
  (optional-email :nook-email nook-email)
  (optional-email :kindle-email kindle-email)
  (vali/rule (vali/is-email? email)
    [:email "Please enter a valid email address"])
  (not (dbg (vali/errors? :email :nook-email :kindle-email))))

(defpage [:get "/secure/settings"] {:as req}
  (settings-view (requtil/get-user)))

(defpage [:post "/secure/settings" ] {:as req}
  (if (settings-valid? (dbg req))
    (let [u (requtil/get-user)]
      (db/put-user! (coll/assoc-keys u req [:email :nook-email :kindle-email :email-opt-out]))
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
    (dbg (if (empty? (:email u))
      (redirect-to-settings)
      (response/redirect (requtil/absolute-url "/"))))))

(defn make-provider-google []
  (oauth/make-provider-google "http://www-opensocial.googleusercontent.com/api/people/"))

(defpage "/login" {:as req}
  (login-view))

(defpage "/logout" {:as req}
  (session/remove! :user-id)
  (response/redirect "/"))

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

