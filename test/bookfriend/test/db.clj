(ns bookfriend.test.db
  (:require [appengine-magic.testing :as ae-testing])
  (:use [bookfriend.db])
  (:use [clojure.test]))

(use-fixtures :each (ae-testing/local-services :all))

(deftest get-loan-recip-sorts-by-points
  (put-user! (create-user-entity "u1" "name" "u1@example.com" "u1-kindle@example.com" "u1-nook@example.com" 0))
  (put-user! (create-user-entity "u2" "name2" "u2@example.com" "u2-kindle@example.com" "u2-nook@example.com" 0))
  (put-user! (create-user-entity "u3" "name2" "u3@example.com" "u3-kindle@example.com" "u3-nook@example.com" 0))
  (set-user-points "u2" 20)
  (set-user-points "u3" 30)
  (put-book! (create-book-entity "b1" "nook" "a1" "t1" nil nil 0))
  (put-book-user "u1" "b1" "have" "")
  (put-book-user "u2" "b1" "want" "")
  (put-book-user "u3" "b1" "want" "")
  (is (="u3" (:id (get-loan-recip "b1")))))
