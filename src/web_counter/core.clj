(ns web-counter.core
  (:import [net.spy.memcached MemcachedClient ConnectionFactoryBuilder ConnectionFactoryBuilder$Protocol])
  (:import [net.spy.memcached.auth AuthDescriptor PlainCallbackHandler])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream File])
  (:import [javax.imageio ImageIO])
  (:import [java.net InetSocketAddress])
  (:import [java.awt.image BufferedImage])
  (:import [java.awt Graphics]))

(set! *warn-on-reflection* true)

(def counter-key "page-counter")

(declare memcached-client)

(defn make-memcached-client []
  (let [servers (map (fn [^String s] 
        (let [sp (.split s ":")
              ^String host (aget sp 0)
              ^Integer port (if (> (alength sp) 1) (Integer/parseInt (aget sp 1)) 11211)]
          (InetSocketAddress. host port)))
        (.split (System/getenv "MEMCACHE_SERVERS") ";"))
        username (System/getenv "MEMCACHE_USERNAME")
        password (System/getenv "MEMCACHE_PASSWORD")
        callback-handler (PlainCallbackHandler. username password)
        auth-descriptor (AuthDescriptor. (into-array ["PLAIN"]) callback-handler)
        builder (-> (ConnectionFactoryBuilder.)
                  (.setAuthDescriptor auth-descriptor)
                  (.setProtocol (ConnectionFactoryBuilder$Protocol/BINARY)))
        conn-factory (.build builder)] 
    (let [^MemcachedClient client (MemcachedClient. conn-factory servers)]
      (if (nil? (.get client counter-key)) (.set client counter-key 0 0))
      client)))

(def counter-images
  (reduce (fn [cache n]
    (let [res (clojure.java.io/resource (str "odometer/" n ".gif"))]
      (assoc cache (format "%d" n) (ImageIO/read res)))) {} (range 10)))

(defn init []
  (alter-var-root (var memcached-client) (fn [x] (make-memcached-client))))

(defn make-gif-counter [n]
  (let [numStr (format "%d" n)
        images (map #(get counter-images (String/valueOf %1)) (.toCharArray numStr))
        width (reduce (fn [w ^BufferedImage img] (+ w (.getWidth img))) 0 images)
        height (reduce (fn [h ^BufferedImage img] (max h (.getHeight img))) 0 images)
        output (ByteArrayOutputStream.)
        img (BufferedImage. width height BufferedImage/TYPE_BYTE_INDEXED)
        ^Graphics graphics (.createGraphics img)]

    (loop [xOffset 0 digit-images images]
      (when-let [^BufferedImage digit-image (first digit-images)]
        (.drawImage graphics digit-image xOffset 0 nil)
        (recur (+ xOffset (.getWidth digit-image)) (rest digit-images))))
    (.dispose graphics)
    (ImageIO/write img "GIF" output)
    (.toByteArray output)))

(defn app [request]
  (case (:uri request)
    "/counter"
      (let [^MemcachedClient mc memcached-client
            ^String k counter-key
            n (.incr mc k 1)]
        {:status 200
         :headers {"Content-Type" "image/gif"}
         :body (ByteArrayInputStream. (make-gif-counter n))})
    "/"
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body (str "<html><body><p>You are visitor number:</p><img src=\"/counter\"/><br/><br/>Read about this site <a href=\"http://www.chrismoos.com/2013/01/31/clojure-ring-and-1990s-counters/\">here</a>.</body></html>")}
    {:status 404}))
