(ns tractatus.inventory
  "An inventory describes resources. This namespace provides a
  comprehensive specification (in spec) of the inventory as well as a
  DSL to build inventories."
  (:require [clojure.spec.alpha :as s]))

(s/def ::name keyword?)
(s/def ::tablename string?)
(s/def ::primary-key keyword?)
(s/def ::cardinality #{:belongs-to :has-many})
(s/def ::association (s/keys :req-un [::name
                                      ::cardinality]))
(s/def ::associations (s/coll-of ::association))
(s/def ::callback fn?)
(s/def ::callbacks (s/map-of #{:create
                               :created
                               :update
                               :updated
                               :destroy
                               :destroyed}
                             (s/coll-of ::callback)))
(s/def ::persistence (s/map-of #{:get
                                 :find
                                 :create!
                                 :update!
                                 :delete!}
                               fn?))
(s/def ::resource (s/keys :req-un [::name]
                          :opt-un [::tablename
                                   ::primary-key
                                   ::associations
                                   ::callbacks
                                   ::persistence]))
(s/def ::resources (s/coll-of ::resource))
(s/def ::inventory (s/keys :req-un [::resources]
                           :opt-un [::primary-key]))

;; TODO: switch to fully qualified namespaces

(defn- validate [inventory spec]
  (when-not (s/valid? spec inventory)
    (throw (ex-info "Invalid resource inventory"
                    (s/explain-data spec inventory))))
  ;; return inventory so validate can be used in threading macros
  inventory)

(defn datasource
  [inventory ds]
  (assoc inventory :datasource ds))

(def ^:private base-inventory
  (-> {:primary-key :id}
      ;; (datasource (tractatus.persistence.atom/make-atomdb))
      ))

(defmacro defresources
  [& body]
  `(-> base-inventory
       ~@body
       (validate ::inventory)
       ;; TODO: use resulting data structure to setup resource records
       ))

(def ^:private inherited-attributes
  [:datasource])

(defmacro resource
  [inventory resource-name & body]
  `(assoc-in ~inventory
             [:resources ~resource-name]
             (-> (select-keys ~inventory inherited-attributes) ~@body)))

(defn tablename
  "Overwrites the predefined table name."
  [inventory name]
  (assoc inventory :tablename name))

(defn primary-key
  "Overwrites the predefined primary key."
  [inventory name]
  (assoc inventory :primary-key name))

(defn- add-association [inventory cardinality resource-name]
  (assoc-in inventory [:associations resource-name :cardinality] cardinality))

(defn has-many [inventory resource-name]
  (add-association inventory :has-many resource-name))

(defn belongs-to [inventory resource-name]
  (add-association inventory :belongs-to resource-name))

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
