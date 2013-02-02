(defproject web-counter "1.0.0-SNAPSHOT"
  :description "web counters are so cool"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [ring "1.1.8"]
                 [spy/spymemcached "2.8.1"]]
  :plugins [[lein-ring "0.8.2"]]
  :min-lein-version "2.0.0"
  :repositories [["couchbase" {:url "http://files.couchbase.com/maven2/" :checksum :warn}]]
  :ring {:handler web-counter.core/app :init web-counter.core/init})

