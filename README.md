# bookfriend

Source code for the webapp:  http://bookfriend.me/

A matchmaking site for Kindle and Nook owners to allow them to share books with
each other.

The site uses:

* Google App Engine (with appengine-magic)
* Noir

## Dev Notes

    ;; To test URL fetch when in REPL:
    (require '[appengine-magic.local-env-helpers :as ae-helpers])
    (ae-helpers/appengine-init (java.io.File. ".") 8090)

    ;; To run dev server:
    ;; Start REPL, then run:
    (require '[appengine-magic.core :as ae])
    (require '[bookfriend.app_servlet :as app])
    (ae/serve app/bookfriend-app)

    ;; To stop / start
    (ae/stop)
    (ae/serve app/bookfriend-app)

## Pushing to App Engine ##

    lein appengine-prepare
    ~/bin/appengine-java-sdk-1.5.4/bin/appcfg.sh update war

## Data model notes

    Entities:
      - book
      - user
      - book-user
      - loan

    

## Migration from old system

### Data import

    lein uberjar
    java -cp bookfriend-1.0.0-SNAPSHOT-standalone.jar bookfriend.dataimport localhost:3306:user:pw localhost:8080
    

## License

Copyright (C) 2011 James Cooper

Distributed under the Eclipse Public License, the same as Clojure.
