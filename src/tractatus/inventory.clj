(ns tractatus.inventory
  "An inventory describes resources. This namespace provides a
  comprehensive specification (in spec) of the inventory as well as a
  DSL to build inventories."
  (:require [clojure.spec.alpha :as s]
            [tractatus.inflection :as i]))

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
(s/def ::inventory (s/keys :opt-un [::primary-key
                                    ::resources]))

(defn validate [inventory spec]
  (when-not (s/valid? spec inventory)
    (throw (ex-info "Invalid resource inventory"
                    (s/explain-data spec inventory))))
  ;; return inventory so validate can be used in threading macros
  inventory)

(defn datasource
  [inventory ds]
  (assoc inventory :datasource ds))

(defn retrievable
  [inventory ds]
  (assoc inventory :retrievable ds))

(defn persistable
  [inventory ds]
  (assoc inventory :persistable ds))

(def base-inventory
  (-> {:primary-key :id}
      ;; (datasource (tractatus.persistence.atom/make-atomdb))
      ))

(defmacro definventory
  [inventory-name & body]
  `(def ~inventory-name
     (-> base-inventory
         ~@body
         (validate ::inventory)
         ;; TODO: use resulting data structure to setup resource records
         )))

#_(definventory my-inventory)

(def inherited-attributes
  [:datasource
   :retrievable
   :persistable])

(defmacro resource
  [inventory resource-name & body]
  `(assoc-in ~inventory
             [:resources ~resource-name]
             (-> (select-keys ~inventory inherited-attributes)
                 (assoc :name ~resource-name)
                 ~@body)))

(defn tablename
  "Overwrites the predefined table name."
  [inventory name]
  (assoc inventory :tablename name))

(defn primary-key
  "Overwrites the predefined primary key."
  [inventory name]
  (assoc inventory :primary-key name))

(defn- add-association [inventory association]
  (assoc-in inventory [:associations (:name association)] association))

(defn has-many
  ([inventory resource-name] (has-many inventory resource-name {}))
  ([inventory resource-name opts]
   (let [resource-name (i/pluralize resource-name)]
     (add-association inventory (merge {:cardinality :has-many
                                        :resource-name resource-name
                                        :name resource-name}
                                       opts)))))

(defn belongs-to
  ([inventory resource-name] (belongs-to inventory resource-name {}))
  ([inventory resource-name opts]
   (let [resource-name (i/singularize resource-name)]
     (add-association inventory (merge {:cardinality :belongs-to
                                        :resource-name resource-name
                                        :name resource-name}
                                       opts)))))

;;(defn- vectorify [value]
;;  (if (sequential? value) value [value]))
;;
;;(defn- vectorify-values [a-map]
;;  (reduce-kv (fn [m k v] (assoc m k (vectorify v))) {} a-map))
;;
;;(defn add-callbacks [inventory callback-map]
;;  (update inventory :callbacks #(merge-with concat % (vectorify-values callback-map))))
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
;;  [inventory hook handler]
;;  {:pre [(#{:create
;;            :created
;;            :update
;;            :updated
;;            :destroy
;;            :destroyed} hook)]}
;;  (add-callbacks inventory {hook [handler]}))
