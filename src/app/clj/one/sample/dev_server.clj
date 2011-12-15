(ns one.sample.dev-server
  "Serve a friendly ClojureScript environment with code reloading and
   the ClojureScript application in both development and advanced
   compiled mode."
  (:use [ring.adapter.jetty :only (run-jetty)]
        [ring.middleware.file :only (wrap-file)]
        [ring.middleware.file-info :only (wrap-file-info)]
        [ring.middleware.params :only (wrap-params)]
        [ring.middleware.stacktrace :only (wrap-stacktrace)]
        [ring.util.response :only (file-response)]
        [compojure.core :only (defroutes GET POST ANY)]
        [cljs.repl :only (repl)]
        [cljs.repl.browser :only (repl-env)]
        [one.templates :only (load-html apply-templates)]
        [one.host-page :only (application-host)]
        [one.sample.api :only (remote-routes)]
        [one.sample.config])
  (:require [net.cgrand.enlive-html :as html]
            [one.reload :as reload]
            [cljs.repl.multi-browser :as multi-browser])
  (:import java.io.File))

(defn- environment [uri]
  (if (= uri "/development") :development :production))

(defn- make-host-page [request]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (application-host config (environment (:uri request)))})

(defroutes app-routes
  remote-routes
  (GET "/development" request (make-host-page request))
  (GET "/production" request (make-host-page request) )
  (GET "/design*" {{file :*} :route-params} (load-html (.substring file 1)))
  (ANY "*" request (file-response "404.html" {:root "public"})))

(defn- js-encoding [handler]
  (fn [request]
    (let [{:keys [headers body] :as response} (handler request)]
      (if (and (= (get headers "Content-Type") "text/javascript")
               (= (type body) File))
        (assoc-in response [:headers "Content-Type"]
                  "text/javascript; charset=utf-8")
        response))))

(defn- rewrite-design-uris [handler]
  (fn [{:keys [uri] :as request}]
    (if (or (.startsWith uri "/design/css")
            (.startsWith uri "/design/javascripts")
            (.startsWith uri "/design/images"))
      (handler (assoc request :uri (.substring uri 7)))
      (handler request))))

(def ^:private app (-> app-routes
                       (reload/watch-cljs "src" config)
                       (wrap-file "public")
                       rewrite-design-uris
                       wrap-file-info
                       apply-templates
                       js-encoding
                       wrap-params
                       wrap-stacktrace
                       (reload/reload-clj (:reload-clj config))))

(defn run-server
  "Start the development server on port 8080."
  []
  (run-jetty (var app) {:join? false :port 8080}))

(defn cljs-repl
  "Start a ClojureScript REPL which can connect to the development
  version of the application. The REPL will not work until the
  development page connects to it, so you will need to either open or
  refresh the development page after calling this function."
  []
  (repl (repl-env)))

(defn cljs-multi-repl
  []
  (repl (multi-browser/repl-env)))

(comment
  ;; One.Sample.the server.
  (use 'one.sample.dev-server :reload-all)
  (run-server)
  ;; Start a REPL.
  (cljs-repl)
  ;; Don't forget to open the development page, or refresh it if it's
  ;; already open, or the REPL won't work

  ;; Start a multi-browser REPL
  (cljs-multi-repl)

  )
