(ns bookfriend.email
  (:use [bookfriend.util])
  (:use [hiccup.core])
  (:require [appengine-magic.services.mail :as mail]))

(def our-email "bookfriend.me alerts <info@bookfriend.me>")

(defn email-body [contents]
  (html
    [:html
     [:head ]
     [:body
      [:div {:style "width: 100%; background-color: #eef0ef;
           font-family: Helvetica, Arial, Sans-Serif; font-size: 14px;" }
        [:div {:style "width: 90%; padding: 20px; color: #374546; line-height: 1.3;" }
          [:img {:src "http://bookfriend.me/css/images/bookfriendme-logo.png" } ]
          contents ] ] ]]))

(defn book-row [book show-want-user-id]
  [:tr
   [:td {:valign "middle" }
    [:div {:style "position: absolute; padding: 0; display: inline; float: left;"}
     [:img {:src (format "http://bookfriend.me/static/css/images/%s_small.jpg" (:platform book))
            :style "opacity:0.9;filter:alpha(opacity=90)" } ]]
     (if (:image-url book)
      [:img {:height "100" :src (:image-url book)} ]) ]
   [:td {:valign "middle"}
    [:div {:style "font-size: 14px; line-height: 16px;" } (trunc (:title book) 40) ]
    [:div {:style "font-size: 14px; line-height: 16px;" } (trunc (:author book) 40) ]
    (if show-want-user-id
     [:div {:style "font-size: 14px; line-height: 16px;" }
      [:a {:href (format "http://bookfriend.me/external-want-book?user-id=%s&book-id=%s" show-want-user-id (:id book)) }
       "I want this book" ] ]) ] ])

(defn to-user-info [to-user]
  (list
    [:p [:b "Borrower Contact Info" ] ]
    [:p (str "Name: " (:name to-user))
        [:br ]
        (str "Email: " (:email to-user)) ]))

;;;;;;;;;;;;;;;;;;;;;;;

(defn loan-ack-success [book from-user]
  (mail/send
    (mail/make-message :from our-email
                       :to (:email from-user)
                       :subject (format "Your loan of %s has been acknowledged!" (trunc (:title book) 40))
                       :html-body (email-body (list
        [:p "Hi there," ]
        [:p "The person you loaned this book to just confirmed that they
           got it. You just earned some karma. Nice one." ]
        [:table {:border "0" :cellpading "10"}
         (book-row book nil) ]
        [:p "Thanks for using bookfriend.me!  Remember to visit us when you
              have a book to share!" ] )))))

(defn loan-ack-fail [book from-user to-user]
  (mail/send
    (mail/make-message :from our-email
                       :to (:email from-user)
                       :subject (format "Your loan of %s was not received" (trunc (:title book) 40))
                       :html-body (email-body (list
        [:p "Hi there," ]
        [:p "The person you loaned this book to says they have not received
           it.  We've marked the loan completed in our system, but please
           email them to see if you can figure out why they didn't receive the
           book.  You might need to login to your B&N or Amazon account and
           see if they have record of the loan succeeding" ]
        (to-user-info to-user)
        [:table {:border "0" :cellpading "10"}
         (book-row book nil) ]
        [:p "Thanks for using bookfriend.me!  Remember to visit us when you
              have a book to share!" ] )))))

(defn send-confirm-loan [book from-user to-user]
  (mail/send
    (mail/make-message :from our-email
                       :to (:email to-user)
                       :subject (format "%s loaned to your %s" (trunc (:title book) 40) (:platform book))
                       :html-body (email-body (list
        [:p "Hi there," ]
        [:p "A friendly person on the Interwebs just loaned you a book
              using the bookfriend.me service.
              Please confirm that you received it by going to"
         [:a {:href "http://bookfriend.me/"} "bookfriend.me" ]
         ", logging in, and clicking "
         [:b "My Tasks"] ]
        [:p "If you did not receive the book, try emailing the lender
              using the contact information below." ]
        [:p [:b "Lender Contact Info" ] ]
        [:p (str "Name: " (:name from-user))
            [:br ]
            (str "Email: " (:email from-user)) ]
        [:table {:border "0" :cellpading "10"}
         (book-row book nil) ]
        [:p "Thanks for using bookfriend.me!  Remember to visit us when you
              have a book to share!" ] )))))

(defn send-loan-created [book from-user to-user]
  (mail/send
    (mail/make-message :from our-email
                       :to (:email from-user)
                       :subject (format "Thank you for lending %s" (trunc (:title book) 40))
                       :html-body (email-body (list
        [:p "Hi there," ]
        [:p "Thanks for using bookfriend.me to loan a book. The borrower
           has been notified via email and has been asked to acknowledge
           that they received the book.  Use the contact info below if you
           need to reach them." ]
        (to-user-info to-user)
        [:table {:border "0" :cellpading "10"}
         (book-row book nil) ]
        [:p "Thanks for using bookfriend.me!  Remember to visit us when you
              have a book to share!" ] )))))
