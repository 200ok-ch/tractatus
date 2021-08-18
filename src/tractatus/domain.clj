(ns tractatus.domain
  "A domain describes resources. This namespace provides a comprehensive
  specification (in spec) of the data structure that represents a
  domain as well as a DSL to build domains programmtically."
  (:require [clojure.spec.alpha :as s]
            [tractatus.inflection :as ti]
            [tractatus.persistence :as tp]
            [clojure.string :as str]))

;; TODO: switch to fully qualified namespaces

;;; specification of the domain data structure

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

(s/def ::domain (s/keys :opt-un [::primary-key
                                 ::resources]))

;; threadable domain validation helper

(defn validate [domain spec]
  (when-not (s/valid? spec domain)
    (throw (ex-info "Invalid resource domain"
                    (s/explain-data spec domain))))
  ;; return domain so validate can be used in threading macros
  domain)

;;; DSL to build domains programmtically

(defn datasource
  [domain ds]
  (assoc domain :datasource ds))

(defn retrievable
  [domain ds]
  (assoc domain :retrievable ds))

(defn persistable
  [domain ds]
  (assoc domain :persistable ds))

;; TODO: check if there is a better way than passing quoted symbols,
;; which require a resolve-deref on the other side
(def base-domain
  (-> {:primary-key :id}
      (retrievable {:find-by-id 'tractatus.persistence/find-by-id
                    :find-by-conditions 'tractatus.persistence/find-by-conditions})
      (persistable {:insert! 'tractatus.persistence/insert!
                    :update! 'tractatus.persistence/update!
                    :delete! 'tractatus.persistence/delete!})
      ;; (datasource (tractatus.persistence.atom/make-atomdb))
      ))

(defmacro defdomain
  [domain-name & body]
  `(def ~domain-name
     (-> base-domain
         ~@body
         ;; (validate ::domain)
         ;; TODO: use resulting data structure to setup resource records
         )))

#_(defdomain my-domain)

(def inherited-attributes
  [:datasource
   :retrievable
   :persistable])

(defmacro resource
  [domain resource-name & body]
  `(assoc-in ~domain
             [:resources ~resource-name]
             (-> (select-keys ~domain inherited-attributes)
                 (assoc :name ~resource-name)
                 (assoc :tablename (str/replace (ti/pluralize (name ~resource-name)) #"-" "_"))
                 ~@body)))

(defn tablename
  "Overwrites the predefined table name."
  [domain name]
  (assoc domain :tablename name))

(defn primary-key
  "Overwrites the predefined primary key."
  [domain name]
  (assoc domain :primary-key name))

(defn- add-association [domain association]
  (assoc-in domain [:associations (:name association)] association))

(defn has-many
  ([domain resource-name] (has-many domain resource-name {}))
  ([domain resource-name opts]
   (let [resource-name (ti/pluralize resource-name)]
     (add-association domain (merge {:cardinality :has-many
                                     :resource-name resource-name
                                     :name resource-name}
                                    opts)))))

(defn belongs-to
  ([domain resource-name] (belongs-to domain resource-name {}))
  ([domain resource-name opts]
   (let [resource-name (ti/singularize resource-name)]
     (add-association domain (merge {:cardinality :belongs-to
                                     :resource-name resource-name
                                     :name resource-name}
                                    opts)))))

;;(defn- vectorify [value]
;;  (if (sequential? value) value [value]))
;;
;;(defn- vectorify-values [a-map]
;;  (reduce-kv (fn [m k v] (assoc m k (vectorify v))) {} a-map))
;;
;;(defn add-callbacks [domain callback-map]
;;  (update domain :callbacks #(merge-with concat % (vectorify-values callback-map))))
;;
;;(defn add-callback
;;  "To install a callback, `hook` is one of the following keywords:
;;
;;    :create
;;    :created
;;    :update
;;    :updated
;;    :destroy
;;    :destroyed
;;
;;  and value is a callback function that takes
;;
;;    etype - the entity type the callback was registered on
;;    hook  - the keyword the callback was registered for
;;    emap  - entity map of the entity currently being processed
;;
;;  and returns one of the following, in case of success:
;;
;;    true          - identity of emap, no errors
;;    a map         - modified emap, no errors
;;
;;  xor one of the following, in case of failure:
;;
;;    false         - unspecified error
;;    a string      - an error message
;;    a vector/list - multiple errors
;;    other         - error with error object
;;
;;  If a before callback returns an error the action will not be
;;  performed. If the errors key `:halt` evaluates to true the callback
;;  chain will be halted entirely."
;;  [domain hook handler]
;;  {:pre [(#{:create
;;            :created
;;            :update
;;            :updated
;;            :destroy
;;            :destroyed} hook)]}
;;  (add-callbacks domain {hook [handler]}))
