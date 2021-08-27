(ns tractatus.dsl
  (:require [tractatus.inflection :as ti]
            [tractatus.persistence :as tp]))

;;; DSL to build resource aspects programmtically

(defn datasource
  [aspects ds]
  (assoc aspects :datasource ds))

(defn retrievable
  [aspects fn-map]
  (assoc aspects :retrievable fn-map))

(defn persistable
  [aspects fn-map]
  (assoc aspects :persistable fn-map))

(defn transformable
  [aspects fn-map]
  (assoc aspects :transformable fn-map))

;; (def ^:private merge-with-concat
;;   (partial merge-with concat))
;;
;; #_(update {:a {:b [:c]}} :a merge-with-concat {:b [:d]})
;;
;; (defn add-transformations
;;   [aspects value]
;;   (update aspects :transformable merge-with-concat value))
;;
;; (defn add-read-transformation
;;   [aspects f]
;;   (add-transformations aspects {:read [f]}))
;;
;; (defn add-write-transformation
;;   [aspects f]
;;   (add-transformations aspects {:write [f]}))

(def inherited-attributes
  [:primary-key
   :datasource
   :retrievable
   :persistable
   :transformable
   :callbacks])

;; TODO: thos should probably be renamed to `base` pr something like that
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
