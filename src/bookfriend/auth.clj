(ns bookfriend.auth)

;(defn logged-in? []
;  (or (user/user-logged-in?) (session/get :user-id)))
;
;(defn user-display-name []
;  (if (user/user-logged-in?)
;    (user/current-user)
;    (session/get :user-display-name)))
;
;(defn wrap-auth
;  [handler]
;  (fn [request]
;    (binding [*request* request]
;      (handler request))))
