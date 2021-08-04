(ns tractatus.domain
  "A domain describes resources. This namespace provides a comprehensive
  specification (in spec) of the data structure that represents a
  domain as well as a DSL to build domains programmtically."
  (:require [clojure.spec.alpha :as s]
            [tractatus.inflection :as ti]
            [tractatus.persistence :as tp]))

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
  [domain fn-map]
  (assoc domain :retrievable fn-map))

(defn persistable
  [domain fn-map]
  (assoc domain :persistable fn-map))

(defn transformable
  [domain fn-map]
  (assoc domain :transformable fn-map))

;; (def ^:private merge-with-concat
;;   (partial merge-with concat))
;;
;; #_(update {:a {:b [:c]}} :a merge-with-concat {:b [:d]})
;;
;; (defn add-transformations
;;   [domain value]
;;   (update domain :transformable merge-with-concat value))
;;
;; (defn add-read-transformation
;;   [domain f]
;;   (add-transformations domain {:read [f]}))
;;
;; (defn add-write-transformation
;;   [domain f]
;;   (add-transformations domain {:write [f]}))

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
  [:primary-key
   :datasource
   :retrievable
   :persistable
   :transformable
   :callbacks])

(defmacro resource
  [domain resource-name & body]
  `(assoc-in ~domain
             [:resources ~resource-name]
             (-> (select-keys ~domain inherited-attributes)
                 (assoc :name ~resource-name)
                 (assoc :tablename (ti/pluralize (name ~resource-name)))
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
  ([domain resource-name]
   (has-many domain resource-name {}))
  ([domain resource-name opts]
   (add-association domain (merge {:cardinality :has-many
                                   :resource-name (ti/singularize resource-name)
                                   :name (ti/pluralize resource-name)}
                                  opts))))

(defn belongs-to
  ([domain resource-name]
   (belongs-to domain resource-name {}))
  ([domain resource-name opts]
   (let [resource-name (ti/singularize resource-name)]
     (add-association domain (merge {:cardinality :belongs-to
                                     :resource-name resource-name
                                     :name resource-name}
                                    opts)))))

(defn- vectorify [value]
  (if (sequential? value) value [value]))

(defn- vectorify-values [a-map]
  (reduce-kv (fn [m k v] (assoc m k (vectorify v))) {} a-map))

(defn add-callbacks [domain callback-map]
  (update domain :callbacks #(merge-with concat % (vectorify-values callback-map))))

(defn add-callback
  "To install a callback, HOOK is one of the following keywords:

   :create
   :created
   :update
   :updated
   :destroy
   :destroyed

  and HANDLER is a function that takes either 1, 2, or 3 arguments

   attrs    - resource map (required)
   resource - the resource type the callback was registered on (optional)
   hook     - the keyword the callback was registered for (optional)

  The handler function has to return one of the following.

  In case of success:

   true          - identity of resource attrs, no errors
   a map         - modified resource attrs, no errors

  In case of failure:

   false         - unspecified error
   a string      - an error message
   a vector/list - multiple errors
   other         - error with error object

  If a before callback returns an error the action will not be
  performed. If the errors key `:halt` evaluates to true the callback
  chain will be halted entirely."
  [domain hook handler]
  {:pre [(#{:create
            :created
            :modify
            :modified
            :destroy
            :destroyed} hook)
         (fn? handler)]}
  (add-callbacks domain {hook [handler]}))
