# bookfriend

Source code for the webapp:  http://bookfriend.me/

A matchmaking site for Kindle and Nook owners to allow them to share books with
each other.

The site uses:

* Google App Engine (with appengine-magic)
* Noir

## Dev Notes

    To test URL fetch when in REPL:
    
    (require '[appengine-magic.local-env-helpers :as ae-helpers])
    (ae-helpers/appengine-init (java.io.File. ".") 8090)



## License

Copyright (C) 2011 James Cooper

Distributed under the Eclipse Public License, the same as Clojure.
