(ns tractatus.aspects
  (:require [clojure.spec.alpha :as s]))

;; TODO: switch to fully qualified namespaces

(s/def ::name keyword?)

(s/def ::resource-name keyword?)

(s/def ::cardinality #{:belongs-to :has-many})

(s/def ::association (s/keys :req-un [::name
                                      ::resource-name
                                      ::cardinality]))

(s/def ::associations (s/coll-of ::association))

(s/def ::callback-name #{:create
                         :created
                         :modify
                         :modified
                         :destroy
                         :destroyed})

(s/def ::callback fn?)

(s/def ::callbacks (s/map-of ::callback-name
                             (s/coll-of ::callback)))

(s/def ::retrievable (s/map-of #{:find-by-id :find-by-conditions} fn?))

(s/def ::persistable (s/map-of #{:insert! :update! :delete!} fn?))

(s/def ::tablename string?)

(s/def ::primary-key keyword?)

(s/def ::resource (s/keys :req-un [::name]
                          :opt-un [::tablename
                                   ::primary-key
                                   ::associations
                                   ::callbacks
                                   ::retrievable
                                   ::persistable]))

(s/def ::resources (s/map-of ::resource-name
                             ::resource))

(s/def ::aspects (s/keys :opt-un [::primary-key
                                  ::resources]))

;; threadable aspects validation helper

(defn validate [aspects spec]
  (when-not (s/valid? spec aspects)
    (throw (ex-info "Invalid resource aspects"
                    (s/explain-data spec aspects))))
  ;; return aspects so validate can be used in threading macros
  aspects)
