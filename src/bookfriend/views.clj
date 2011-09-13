(ns bookfriend.views
  (:use [noir.core :only (defpartial render pre-route)])
  (:use [hiccup.core :only (resolve-uri)])
  (:use [hiccup.page-helpers])
  (:use [hiccup.form-helpers])
  (:use [bookfriend.util])
  (:require [noir.validation :as vali])
  (:require [noir.session :as session])
  (:require [bookfriend.requtil :as requtil]))

(defn mock-sidebar-loader []
  { :total-users 302
    :available-books 992
    :activity [
      { :created 1315407000912
        :status "lend"
        :title "Moby Dick"
        :author "Herman Melville"
        :platform "kindle" }
      { :created 1315406000912
        :status "lend"
        :title "Atlas Shrugged"
        :author "Ayn Rand and some other really long text that I don't think will fit in the sidebar if it is not truncated"
        :platform "nook" }
      { :created 1315307000912
        :status "read"
        :title "Programming Clojure"
        :author "Stuart Halloway"
        :platform "kindle" }
      ]})

(def sidebar-loader (atom mock-sidebar-loader))

(defn set-sidebar-loader! [f]
  (swap! sidebar-loader f))

(defn split-newline
  [x]
  (map clojure.string/trim (filter #(not (= "" %)) (clojure.string/split x #"\n"))))

(defn join-newline
  [xs]
  (clojure.string/join "\n" xs))

(defn format-plural [num singular plural]
  (if (= 1 num) singular plural))

(defn format-time-plural [num div singular plural]
  (let [r (.longValue (/ num div))]
    (format "%d %s" r (format-plural r singular plural))))

(defn time-ago [millis]
  (let [sec (.longValue (/ (- (System/currentTimeMillis) millis) 1000))]
    (condp < sec
      604800 (format "%s ago" (format-time-plural sec 604800 "week" "weeks"))
      86400 (format "%s ago" (format-time-plural sec 86400 "day" "days"))
      3600 (format "%s ago" (format-time-plural sec 3600 "hour" "hours"))
      60 (format "%s ago" (format-time-plural sec 60 "minute" "minutes"))
      "moments ago")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defpartial clear-div []
  [:div {:class "cl"} "&nbsp;" ])

(defpartial radio-group-item [name value label checked?]
  [:input {:type "radio" :name name :value value :checked checked?} label ])
  
(defpartial radio-group [name default-val value-labels]
  (let [val (or (requtil/request-param name) default-val)]
    (map #(radio-group-item name (first %) (second %) (= val (first %)) ) value-labels)))

(defpartial header-search-form []
  [:div {:class "book-form"}
   [:form {:id "search-books" :action "/search"}
    [:div {:class "rowElem"}
     [:label "Search books:"]
     [:span {:class "field"}
      [:input {
        :class "blink" :type "text" :name "keyword" :size "50"
        :value (or (requtil/request-param "keyword") "Enter author or title")
        :onclick "if (this.value==='Enter author or title') { this.value = ''; }" } ] ] ]
    [:div {:class "search_options_wrapper"}
     [:div {
       :class "button gray" :style "clear: none;" :id "search-button"
       :onclick "document.forms['search-books'].submit(); return false" }
      [:div {:class "left" :style="clear: none;"}]
      [:div {:class "center" }
       [:a {:href "#search"} "Search" ] ]
      [:div {:class "righty"} ] ] ]
    [:div {:class "rowElem"}
     [:label "Show books for:"]
     [:div {:class "books"}
      [:div {:class "book"}
       (radio-group "platform" "both" [["nook" "Nook"] ["kindle" "Kindle"] ["both" "Both"]]) ] ] ]
    (clear-div) ] ] )

(defpartial header [title]
  [:div {:id "lend" } ]
  [:div {:id "header" }
   [:div {:class "shell"}
    (clear-div)
    [:h1 {:id "logo" :class "notext"}
     [:a {:href "/" } "bookfriend.me" ] ]
    (clear-div)
    (if (requtil/logged-in?)
      (let [user (requtil/get-user)]
        (list
          [:div {:class "logged_in"}
            [:p (str "Welcome " (:name user))]
            [:span {:class "points"}
             [:span (str (:points user) " points")] ]
            [:img {:src "/css/images/nav-left.png"}]
            [:span {:class "top-buttons"}
             [:a {:href "/secure/mytasks"} [:span "My Tasks"] ]
             [:a {:href "/secure/mybooks"} [:span "My Books"] ]
             [:a {:href "/secure/settings"} [:span "Settings"] ]
             [:a {:href "/faq"} [:span "FAQ"] ]
             [:a {:href "/logout"} [:span "Logout"] ] ]
            [:img {:src "/css/images/nav-right.png"} ] ] ))
      (list
        [:div {:class "top-btns"}
         [:a {:href "/login"} [:span "Login"] ]
         [:a {:href "/faq"} [:span "FAQ"] ] ] ) )
    (clear-div)
    (header-search-form)
    (clear-div) ] ] )

(defpartial recent-activity-row [row]
  [:div {:class "activity"}
   [:span {:class "time"} (time-ago (:created row)) ]
   " Someone wants to "
   [:span {:class (:status row) } (str (:status row) " ") ]
   [:span {:class "booktitle"} (trunc (:title row) 30) ]
   " by "
   [:span {:class "author"} (trunc (:author row) 30) ]
   (str " for the " (:platform row)) ])

(defpartial sidebar []
  (let [stats (@sidebar-loader)]
    (list
      [:div {:id "sidebar" :class "right"}
       [:h3 {:class "title"} "Our Community"]
       [:div {:class "recent-activity"}
        [:div {:class "instructions"}
         (str (:total-users stats) " users are sharing " (:available-books stats) " books")]
        [:div {:class "instructions"}
         [:a {:class "searchhave" :href "/available"} "View all available books"] ] ]
       [:h3 {:class "title"} "Recent Activity"]
       [:div {:class "recent-activity"}
        (map recent-activity-row (:activity stats)) ] ]
      (clear-div) )))

(defpartial body [content]
  [:div {:id "main" }
   (clear-div)
   [:div {:class "shell"}
    [:div {:class "main-inner"}
     [:div {:class "main-inner-b"}
      [:div {:id "content" :class "left" } content ]
      (sidebar)
      (clear-div)
      ] ] ] ])

(defpartial footer []
  [:div {:id "footer"}
   [:div {:class "shell"}
    [:p {:class "copyrights"}
     [:iframe {:src "http://www.facebook.com/plugins/like.php?href=http%3A%2F%2Fnooklend.me%2F&layout=standard&show_faces=true&width=340&action=like&colorscheme=light&height=20\""
               :scrolling "no" :frameborder "0" :style "border:none; overflow:hidden; width:340px; height:20px;" }] ]
    [:p {:class "copyrights" } "Development by James Cooper &bull; Design by Ben Fogarty &bull; &copy; 2010-2011 Bitmechanic LLC &bull; Nook and LendMe are registered trademarks of Barnes and Noble Inc." ] ] ]
  [:div {:id "notlendable_tooltip" :class "tooltip"}
   "Some users think this book is not lendable.  This means that while it
    is an e-book, the publisher is not allowing users to share it with
    others.  This may change in the future."]
  (javascript-tag "jQuery(\"a.tooltiplink\").simpletooltip();")
  (javascript-tag "var _gaq = _gaq || [];
            _gaq.push(['_setAccount', 'UA-2855697-3']);
            _gaq.push(['_trackPageview']);
            (function() {
            var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
            ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
            var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
            })();")
  (javascript-tag "var uservoiceOptions = {
            key: 'bookfriendme',
            host: 'bookfriendme.uservoice.com',
            forum: '82857',
            showTab: true,
            /* optional */
            alignment: 'left',
            background_color:'#374546',
            text_color: 'white',
            hover_color: '#909e9f',
            lang: 'en'
            };
            function _loadUserVoice() {
            var s = document.createElement('script');
            s.setAttribute('type', 'text/javascript');
            s.setAttribute('src', ('https:' == document.location.protocol ? 'https://' : 'http://') + 'cdn.uservoice.com/javascripts/widgets/tab.js');
            document.getElementsByTagName('head')[0].appendChild(s);
            }
            _loadSuper = window.onload;
            window.onload = (typeof window.onload != 'function') ? _loadUserVoice : function() { _loadSuper(); _loadUserVoice(); };")
  (include-js "http://e.onetruefan.com/js/widget.js"))

(defpartial layout [title & content]
  (html5
    [:head
     [:title (str "bookfriend.me - " title)]
     [:link {:rel "shortcut icon" :href "/css/images/favicon.ico?cb=1" }]
     (include-css "/css/style.css" "/css/jquery.loadmask.css"
                  "/css/cssbuttons/cssbuttons.css"
                  "/css/cssbuttons/skins/sample/sample.css")
     (include-js  "/js/jquery-1.4.2.min.js"
                  "/js/depagify.jquery.js"
                  "/js/jquery.simpletooltip-min.js")]
    [:body
     (header title)
     (body content)
     (footer)]))

(defpartial flash-message []
  (let [f-msg (session/flash-get)]
    (if f-msg
      [:ul {:class "flashes"}
       [:li f-msg ] ] )))

(defpartial err-message []
  (if (vali/errors?)
    [:ul {:class "errors"}
       [:li "Please correct the fields in red"] ] ))

(defn flash-and-err-messages []
  (list (flash-message) (err-message)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defpartial error-item [[first-error]]
  [:p.error first-error])

(defn submit-link [form-name label]
  [:div { :class "submit-buttons" }
   [:a { :href "#"
         :onclick (format "document.getElementById('%s').submit(); return false;" form-name)
         :class "btn blue-btn" }
    [:span label ] ] ] )

(defn label-err [name label]
  (if (vali/errors? name)
    [:span {:class "error"}
     [:label {:for name} label ] ]
    [:label {:for name} label ] ))
  
(defn input-with-image [data name label img]
  [:div {:class "input"}
    [:img { :src (str "/css/images/" img) } ]
    (label-err name label)
    (text-field name (name data)) ] )

(defn check-box-div [data name value label]
  (let [checked? (= value (name data))]
    [:div {:class "checkbox"}
     (label-err name label)
     (check-box name checked? value) ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defpartial loan-book-layout [book recip title content]
  [:div {:class "faq"}
   [:h2 title]
   (flash-and-err-messages)
   [:table {:cellspacing "10"}
    [:tr
     [:td {:valign "top"}
      (if (:image-url book)
        [:img {:src (:image-url book) :width "70"} ]) ]
     [:td {:valign "top"} (:steps content) ] ] ]
   [:table
    [:tr
     [:td [:a {:href (format "/secure/loan-book-create?book-id=%s&recip-id=%s" (:id book) (:id recip))
               :class "btn red-btn" } [:span "It Worked!" ] ] ]
     [:td [:a {:href (format "/secure/book-not-loanable?book-id=%s" (:id book))
               :class "btn red-btn"} [:span "Book Isn't Loanable" ] ] ]
     [:td [:a {:href (format "/secure/loan-book-bad-recip?book-id=%s&recip-id=%s" (:id book) (:id recip))
               :class "btn red-btn"} [:span "Recip Email Invalid" ] ] ] ] ]

    [:p "If you have tabs enabled in your web browser, you may need to click back
         to this tab to proceed through the steps." ]
    (:footer content) ])
  
(defpartial loan-book-view-nook [book recip]
  (loan-book-layout book recip "Nook Loan Steps"
    {:steps
      [:ol
       [:li
        [:a {:href "http://my.barnesandnoble.com/ebooks/ebookslibrary.html"
             :target"_blank"} "Click here to login to B&N in a new window" ] ]
       [:li "In the new window, login to your B&N account" ]
       [:li "After logging in you should be on the 'My NOOK Library' page" ]
       [:li "Click 'LendMe' next to the book: " [:b (:title book) ] ]
       [:li "Complete the Loan form using recipient e-mail address: "
        [:b (:nook-email recip) ] ]
       [:li "Click one of the buttons below to tell us what happened" ] ]
     :footer (list
      [:p "If you get lost, here is an example video of me loaning a book at B&N's site." ]
      [:iframe {:src "http://www.screenr.com/embed/dPHs" :width "630" :height "396" :frameborder "0"} ] ) }))

(defpartial loan-book-view-kindle [book recip]
  (loan-book-layout book recip "Kindle Loan Steps"
    {:steps
      [:ol
       [:li
        [:a {:href "https://www.amazon.com/gp/digital/fiona/manage/ref=ya_14"
             :target"_blank"} "Click here to login to Amazon in a new window" ] ]
       [:li "In the new window, login to your Amazon account" ]
       [:li "After logging in you should be on the 'Manage Your Kindle' page" ]
       [:li "Click the 'Actions' button next to the book: " [:b (:title book) ] ]
       [:li "Click the link: 'Loan this title'" ]
       [:li "NOTE: If the book is not lendable, you won't see this link under Actions" ]
       [:li "Complete the Loan form using recipient e-mail address: "
        [:b (:nook-email recip) ] ]
       [:li "Click one of the buttons below to tell us what happened" ] ]
     :footer (list
      [:p "If you get lost, here are Amazon's instructions:" ]
      [:p [:img {:src "/css/images/kindle-loan-howto-small.png" } ] ] ) }))

(defpartial loan-book-view [book recip]
  (layout "Loan Book"
    (condp = (:platform book)
      "kindle" (loan-book-view-kindle book recip)
      "nook" (loan-book-view-nook book recip))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defpartial book-image-cell [book]
  (if (:image-url book)
    [:div {:class "overlay-container"}
     [:img {:class "book_thumbnail" :src (:image-url book) } ]
     [:img {:class "overlay" :style "opacity:0.9;filter:alpha(opacity=90)"
        :src (format "/css/images/%s_small.jpg" (:platform book)) } ] ]))

(defpartial mytasks-view-empty []
  [:h2 {:class "title"} "No Tasks"]
  [:p {:class "instructions" } "You currently have no tasks to complete.
       Check back later to see if someone wants to
       borrow one of your books, or if you have received
       a book from another user." ])

(defpartial mytasks-to-loan-row [book]
  [:tr
   [:td (book-image-cell book) ]
   [:td (trunc (:title book) 40) " by " (trunc (:author book) 40) ]
   [:td {:class "action"}
    [:a {:class "cssbutton sample b loan"
         :href (str "/secure/loan-book?book-id=" (:id book)) }
      [:span "Loan book"] ]]])

(defpartial mytasks-view-to-loan [to-loan]
  (if (not (empty? to-loan))
    [:div {:class "books"}
     [:h2 "Book to loan out"]
     [:table {:class "books-to-loan-out gray-and-wirey"}
      [:thead [:tr [:th "book"] [:th "action"] ] ]
      [:tbody
       (map mytasks-to-loan-row to-loan) ] ] ]))

(defpartial mytasks-to-ack-row [book]
  [:tr
   [:td (book-image-cell book) ]
   [:td (trunc (:title book) 40) " by " (trunc (:author book) 40) ]
   [:td (:from-email book) ]
   [:td {:class "action"}
     [:a {:class "ack"
          :href (str "/secure/book-ack-loan?loan-id=" (:loan-id book)) } "I got it!" ]
     [:br ] [:br ]
     [:a {:class "ack"
          :href (str "/secure/book-ack-loan-fail?loan-id=" (:loan-id book)) } "No, the loan failed" ] ] ])

(defpartial mytasks-view-to-ack [to-ack]
  (if (not (empty? to-ack))
    [:div {:class "books"}
     [:h2 "We think someone loaned you these book"]
     [:table {:class "books-to-loan-out gray-and-wirey"}
      [:thead [:tr [:th "book"] [:th "loaned by"] [:th "action"] ] ]
      [:tbody
       (map mytasks-to-ack-row to-ack) ] ] ]))

(defpartial mytasks-view [to-loan to-ack]
  (layout "My Tasks"
    (if (and (empty? to-loan) (empty? to-ack))
      (mytasks-view-empty)
      (list
        (mytasks-view-to-loan to-loan)
        (mytasks-view-to-ack to-ack)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defpartial mybooks-row [book]
  (let [id (:id book)]
    [:tr {:id (str "book-" id)}
     [:td {:class "cover"} (book-image-cell book) ]
     [:td {:class "titleAuthor"} (trunc (:title book) 40) " by " (trunc (:author book) 40) ]
     [:td
      [:a {:href (str "/secure/mybooks?cancel=" id) :class "cssbutton sample b" }
       [:span "remove"] ] ] ]))

(defpartial mybooks-table [title books]
  (if (not (empty? books))
    (list
      [:h2 title]
      [:table {:class "books-i-want gray-and-wirey"}
       [:thead
        [:tr
         [:th {:class "cover"} "cover"] [:th "book"] [:th "action"] ]]
       [:tbody
        (map mybooks-row books) ]] )))

(defpartial mybooks-view [books]
  (layout "My Books"
    [:h2 {:class "title"} "My Books" ]
    (if (empty? books)
      [:p {:class "instructions"} "You currently have no books selected.  If you click 'I Want' or
           'I Have' for a book, it will show up here."]
      (list
        (mybooks-table "Books I Want" (books "want"))
        (mybooks-table "Books I Have" (books "have"))
        (javascript-tag "
          function book_cancel(book_id) {
            jQuery.get('book_cancel', { 'book-id' : book_id },
                function(data) {
                    jQuery('#book-'+book_id).remove();
                });
            return false;
          }") ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defpartial book-status-button [status-label book]
  [:div {:style "clear: both;" }
   [:a {:class "cssbutton sample b" :href "#"
        :onclick (format "return book_status('%s', '%s');" (:id book) (first status-label)) }
    [:span (second status-label) ]]])

(defpartial book-status-buttons [book]
  (let [user (requtil/get-user)]
    (if (and user (nil? (:user-status book)))
      (if (contains? (:platforms user) (:platform book))
        (map book-status-button [["want" "I want"] ["have" "I have"] ["dislike" "Don't want"]] (repeat book))
        [:div [:p "To borrow or lend
                   this book, please enter your "
                   (:platform book)
                   " email address in Settings" ] ]))))

(defpartial book-have-want [book]
  (let [ have (:have-count book)
         want (:want-count book)]
    (if (or (> want 0) (> have 0))
      [:div {:class "have-want"}
       (cond
         (and (> want 0) (> have 0))
          (format "%d %s this book and %d %s it"
            have (format-plural have "person has" "persons have")
            want (format-plural want "person wants" "persons want"))
         (> want 0)
          (format "%d %s this book" want (format-plural want "person wants" "persons want"))
         (> have 0)
          (format "%d %s this book" have (format-plural have "person has" "persons have"))) ])))

(defpartial book-user-cancel [book]
  (let [status (:user-status book)
            id (:id book) ]
    (if status
      [:div {:class "have-want"}
       (condp = status
        "have" "You have this book to lend"
        "want" "You want to borrow this book"
        "dislike" "You do not want this book")
       " ("
       [:a {:href "#" :onclick (format "return book_cancel('%s');" id) } "cancel" ]
       ")" ])))

(defpartial book-list-cols [book]
  [:td
    [:div {:class "book-cover overlay-container"}
     (if (not (empty? (:image-url book)))
      [:img {:class "book" :src (:image-url book)}])
      [:img {:class "overlay"
             :src (format "/css/images/%s_small.jpg" (:platform book)) } ] ] ]
   [:td
    [:h3 {:class "title"} (trunc (:title book) 70) ]
    [:p {:class "author"} (trunc (:author book) 40) ]
    (if (> (:not-loanable-count book) 0)
      [:a {:href "#notlendable_tooltip" :class "tooltiplink"} "May not be lendable"] ) 
    (book-have-want book)
    (book-user-cancel book) ]
   [:td {:class "option-buttons"}
    (book-status-buttons book)
    [:div {:style "clear: both;"}
     [:a {:class "cssbutton sample b" :href (:product-url book)
          :target "_blank" } [:span "Book Info" ] ] ]
    (clear-div) ] )

(defpartial book-list-row [book row-css]
  [:tr {:id (str "book-" (:id book))
        :class (str "overlay-container book-item" row-css) }
   (book-list-cols book) ])
   
(defpartial book-list-view [title books]
  (layout title
    [:table {:class "books-holder"}
     [:tbody {:class "books-holder-body"}
      (map book-list-row books (cycle ["" " book-item-odd"])) ] ]
    (javascript-tag "
      function book_cancel(book_id) {
        jQuery.get('/secure/book-cancel', { 'book-id' : book_id },
            function(data) {
                jQuery('#book-'+book_id).html(data);
            });
        return false;
      }

      function book_status(book_id, status) {
        jQuery.get('/secure/book-status', { 'book-id' : book_id, 'status' : status },
            function(data) {
                jQuery('#book-'+book_id).html(data);
            });
        return false;
      }")
    ))

(defpartial settings-view [data]
  (layout "Settings"
    [:h2 {:class "title"} "Settings"]
    (flash-and-err-messages)
    [:form {:id "fsettings" :method "post" :action "/secure/settings" }
     (input-with-image data :nook-email
       "Optional - Email address you use at barnesandnoble.com" "nook_small.jpg")
     (input-with-image data :kindle-email
       "Optional - Email address you use at amazon.com:" "kindle_small.jpg")
     (input-with-image data :email
       "Required - Email address we should send notifications to:" "email_small.jpg")
     (check-box-div data :email-opt-out "0"
       "Email me books available to borrow (~twice a week):")
     (submit-link "fsettings" "Save") ]))

(defpartial login-view-prov [provider]
  [:td
   [:a {:href (str "/login-" provider) }
    [:img {:src (format "/css/images/%s-icon.png" provider)
           :width "32" :align "center" :border "0"} provider ] ] ] )

(defpartial login-view []
  (layout "Login"
    [:h2 {:class "title"} "Login" ]
    [:div {:class "faq"}
     [:p "To get started, please login with your existing
     Twitter, Facebook, or Google account"]
     (clear-div)
     [:table
      [:tbody
       [:tr
        (map login-view-prov ["twitter" "google" "facebook"]) ] ] ] ] ))

(defpartial home-view []
  (layout "Share Nook and Kindle eBooks"
    [:h2 {:class "title"} "Start sharing Nook and Kindle eBooks!" ]
    [:p {:class "instructions"}
     "This site allows Nook and Kindle owners to connect with each
    other and share books. To get started, enter a book author or
    title in the search box that you'd like to lend or borrow."]
    [:p {:class "instructions"}
     "If this is your first visit, please "
     [:a {:href "/faq"} "read the FAQ" ] ] ))

(defpartial faq-view []
  (layout "FAQ"
    [:h2 {:class "title"} "Frequently Asked Questions" ]
    [:div {:class "faq" }
      [:h2 "What is bookfriend.me?" ]
      [:p
        "Amazon and Barnes and Noble both support book lending on their
         Kindle and Nook platforms.
         Their lending features are very similar:"
        [:ul
          [:li "You can lend supported books one time for a period of 14 days" ]
          [:li "Not all books are supported (the publisher must agree)" ]
          [:li "Once you loan the book once, you may not loan it again" ] ] ]
      [:p
        "Our site acts as a \"matchmaker\" connecting people who have purchased a
        book with others who wish to borrow the book.  We do not actually do the
        lending.  The book lending is done on the B&N and Amazon sites." ]
      [:p
        "Search for books you've purchased or books you'd like to borrow.
        If another user wants a book you have available to loan, we'll
        provide their email address
        to you so you can type it into the loan screen on the B&N or
        Amazon web site." ] ]
    [:div {:class "faq" }
      [:h2 "Why do you list books that cannot be loaned?" ]
      [:p
        "Because we don't have an easy way to know whether a book is lendable.
         In the future we plan to provide a way to mark whether books are lendable
         to improve this experience." ]
      [:p
        "The e-book landscape is quite dynamic. Amazon and B&N are
         frequently modifying the lending rules for their books, so if a book is not
         lendable today, that could change tomorrow." ] ]
    [:div {:class "faq" }
      [:h2 "Do you loan the books?" ]
      [:p
       "No. Amazon and B&N each provide a way to loan books from their respective web sites.
        We just connect people by providing a simple way to list the
        books you have and want." ] ]
    [:div {:class "faq" }
      [:h2 "How do I get started?" ]
      [:p
        "Enter an author or book title into the search box, then click
        'I Want' if you would
        like to borrow the book from another user, or 'I Have' if you have
        purchased the book and have it available to loan." ] ]
    [:div {:class "faq" }
      [:h2 "I clicked 'I Want'. Now what happens?" ]
      [:p
       "Now you wait.  When another user clicks 'I Have' for the same book they will receive an
        email telling them someone wants to borrow their book.  When they loan the book to you, you'll
        receive an email from us asking you to acknowledge that you received
        the book." ] ]
    [:div {:class "faq" }
      [:h2 "It looks like I earned some points.  What's that all about?" ]
      [:p "Short answer: To reward loaning and limit mooching" ]
      [:p
       "Long answer: You earn points when you do various things on the site.  You get more points
        for loaning books, but you also earn points for acknowledging that you received a book.
        If more than one user wants a book, the person with the most points will receive the book.
        This will probably become more useful once the site gets bigger and there are longer waiting
        lists for popular books." ] ]
    [:div {:class "faq" }
      [:h2 "Will you send me email? I already get too much." ]
      [:p "We send out two types of email:" ]
      [:ul
        [:li "Pending tasks. We send this 3 times a week to users who need to loan a book to someone
        or acknowledge that they received a book.  To opt out of these you need to remove all
        books using the 'My Books' link.  At that point you're no longer actively participating on
        the site." ]
        [:li "New available books. We send this 2 times a week to all users who have not opted out.
        This email lists available books that no one has clicked 'I Want' for yet. You can opt
        out of this email using the link at the bottom of the email,
        or from the 'Settings' box." ] ] ]
    [:div {:class "faq" }
      [:h2 "I found a bug. How do I contact you?" ]
      [:p
       "Use the 'Feedback' bar on the left. This posts to our uservoice forum.
        You can also contact us on Twitter:"
        [:a {:href "http://twitter.com/bookfriendme" } "@bookfriendme" ] ] ]
    [:div {:class "faq" }
      [:h2 "What if I don't own a Nook or a Kindle?" ]
      [:p
       "No problem!  Although dedicated e-readers are the best way to read eBooks,
        you can download free
        e-reader applications for your computer, iPhone, or Android phone:" ]
      [:ul
        [:li [:a {:href "http://www.barnesandnoble.com/u/free-nook-apps/379002321/" } "Nook software" ] ]
        [:li [:a {:href "http://www.amazon.com/gp/feature.html/ref=amb_link_352814142_11?ie=UTF8&amp;docId=1000493771&amp;pf_rd_m=ATVPDKIKX0DER&amp;pf_rd_s=left-1&amp;pf_rd_r=0T2RZTYXGD1V77F04KQC&amp;pf_rd_t=101&amp;pf_rd_p=1284849722&amp;pf_rd_i=133141011"} "Kindle software" ] ] ]
      [:p
        "Even if you own an e-reader, I suggest trying out the smartphone software.
        I own a Nook e-reader but frequently buy Kindle books and read
        them on my iPhone and MacBook." ] ]
    [:div {:class "faq" }
      [:h2 "Who built this site?" ]
      [:p
        "This site was built by "
        [:a {:href "http://twitter.com/coopernurse"} "James Cooper" ]
        " and "
        [:a {:href "http://benfogarty.com/"} "Ben Fogarty" ] ] ]
    [:div {:class "faq" }
      [:h2 "It is awesome that you built this.  How can I help?" ]
      [:ul
        [:li "Use our site to buy your Amazon and B&N ebooks. The price is the same for you, and we get a small cut." ]
        [:li
          "Become a friend of "
          [:a {:href "http://www.facebook.com/pages/nooklendme/138689352846913" } "our Facebook page" ] ]
        [:li [:a {:href "http://twitter.com/"} "Tweet about us" ] ]
        [:li "E-mail your friends" ]
        [:li "Use the site and have fun!" ] ] ] ) )


