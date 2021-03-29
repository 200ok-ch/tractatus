(ns tractatus.core
  (:require [tractatus.inflection :as inflection]))

;; ;; 1. macros to work with the entity registry (ereg)

;; (defmacro lazy-partial
;;   "Like `partial` but as a macro."
;;   [f & args]
;;   `(fn [& args#]
;;      (apply ~f ~@args args#)))

;; (defmacro defn-with-registry
;;   "Defines the given names as proxies to star-fns with the given
;;   registry as the first parameter."
;;   [ereg & names]
;;   `(do
;;      ~@(for [name# names]
;;          (let [star-fn# (symbol (str name# "*"))]
;;            `(def ~name#
;;               (lazy-partial ~star-fn# ~ereg))))))

;; (defmacro defregistry
;;   "Defines a named registry and the functions rget, find,
;;   create, update, delete in the current namespace."
;;   [name & body]
;;   `(do
;;      (def ~name (-> {} ~@body))
;;      (defn-with-registry ~name
;;        rget
;;        find
;;        create
;;        update
;;        destroy)))

;; ;; 2. Registry building helpers

;; (defmacro register
;;   "For use in a surrounding `defregistry`. Defines an entity. Takes a
;;   symbol as name and a list of registry building helpers. You can
;;   `assoc` to set options for which there are no helpers.

;;   Function overrides

;;     :get-fn      - takes an id (or emap) and returns an emap
;;     :find-fn     - takes an sqlvec and returns a list of emap
;;     :create-fn   - takes an emap
;;     :updated-fn  - takes an emap
;;     :destroy-fn  - takes an emap (or id)

;;   For registered entities there are five functions to be called with
;;   the entity type (etype) as the first parameter.

;;   For retrieving:

;;     (rget etype id-or-emap) ; returns an emap
;;     (find etype sqlvec) ; returns a sequential of emap

;;   For persisting:

;;     (create etype emap)
;;     (update etype emap)
;;     (destroy etype emap-or-id)

;;   Persisting functions return a hashmap with...

;;     :entity   - an (possibly) updated entity map
;;     :errors   - a sequential of error-maps
;;     :affected - number of affected rows, if applicable
;;     :success  - a boolean

;;   The data structure of the registry will look like in this YAML
;;   example:

;;   ---
;;   group:
;;     associations:
;;       memberships:
;;         cardinality: has-many
;;       user:
;;         cardinality: belongs-to
;;   memberships:
;;     associations:
;;       user:
;;         cardinality: belongs-to
;;       group:
;;         cardinality: belongs-to
;;   user:
;;     associations:
;;       memberships:
;;         cardinality: has-many
;;   "
;;   [ereg etype & body]
;;   `(assoc ~ereg ~etype (-> {:tablename (inflection/pluralize (name ~etype))}
;;                            ~@body)))

;; (defn tablename
;;   "Overwrites the predefined table name."
;;   [ereg name]
;;   (assoc ereg :tablename name))

;; (defn- add-association [ereg entity-name cardinality]
;;   (clojure.core/update ereg :associations merge {entity-name {:cardinality cardinality}}))

;; (defn has-many [ereg entity-name]
;;   (add-association ereg entity-name :has-many))

;; (defn belongs-to [ereg entity-name]
;;   (add-association ereg entity-name :belongs-to))

;; (defn- vectorify [value]
;;   (if (sequential? value) value [value]))

;; (defn- vectorify-values [a-map]
;;   (reduce-kv (fn [m k v] (assoc m k (vectorify v))) {} a-map))

;; (defn add-callbacks [ereg callback-map]
;;   (clojure.core/update ereg :callbacks #(merge-with concat % (vectorify-values callback-map))))

;; (defn add-callback
;;   "To install a callback, `hook` is one of the following keywords:

;;     :create
;;     :created
;;     :update
;;     :updated
;;     :destroy
;;     :destroyed

;;   and value is a callback function that takes

;;     etype - the entity type the callback was registered on
;;     hook  - the keyword the callback was registered for
;;     emap  - entity map of the entity currently being processed

;;   and returns one of the following, in case of success:

;;     true          - identity of emap, no errors
;;     a map         - modified emap, no errors

;;   xor one of the following, in case of failure:

;;     false         - unspecified error
;;     a string      - an error message
;;     a vector/list - multiple errors
;;     other         - error with error object

;;   If a before callback returns an error the action will not be
;;   performed. If the errors key `:halt` evaluates to true the callback
;;   chain will be halted entirely."
;;   [ereg hook handler]
;;   (add-callbacks ereg {hook [handler]}))

;; 3. Automatic resolving of associations
;;
;; Here it gets a litle bit more tricky. In order to have nice
;; semantics for resolving associations, we build on the fact that
;; records unlike maps do not implement IFn. Meaning after we're done,
;; our records will implement IFn to support automatic resolving of
;; associations. Hence our upcoming retrieving and persisting function
;; will have to return the emap wrapped into an
;; EntityMap (record). The implementation of ILookup will be the
;; same. So `(:a emap)` in cases where `:a` is an association will
;; still return `nil`. But `(emap :a)` will resolve the association
;; and return the emap of the associated entity.

;; ;; TODO this is rather naive solution and should be based on the
;; ;; central registry for entities
;; (defn- resolve-strategy [emap key]
;;   (cond
;;     (contains? emap (keyword (str (name key) "_id")))
;;     :belongs-to))

;; (defmulti resolve-missing resolve-strategy)

;; (defmethod resolve-missing :default [emap key]
;;   ;; TODO returning nil would be sane
;;   (str "no value for " key))

;; (defmethod resolve-missing :belongs-to [emap key]
;;   ;; TODO actually resolve entity
;;   (str "resolving associated " key))

;; (defrecord EntityMap []
;;   clojure.lang.IFn
;;   (invoke [this key]
;;     (or (get this key)
;;         (resolve-missing this key))))

;; ;; 4. callbacks

;; (defn- run-callback
;;   "Runs the given callback and interprets the result. Returns the
;;   potentially modified emap and a vector of error maps."
;;   [callback etype hook emap]
;;   (let [result (callback etype hook emap)]
;;     (cond
;;       (true? result) [emap []]
;;       (map? result) [result []]
;;       (false? result) [emap [{:error "Unspecified Error"}]]
;;       (sequential? result) [emap result]
;;       (string? result) [emap [{:error result}]]
;;       :default [emap [{:error "Unknown Error" :object result}]])))

;; (defn- run-callbacks [etype hook emap fns]
;;   ;; TODO use (let [[a & b] [1 2 4]] [a b])
;;   (let [callback (first fns)
;;         remaining (rest fns)
;;         [emap1 errors1] (run-callback callback etype hook emap)]
;;     (if (or (empty? remaining) (some :halt errors1))
;;       [emap1 errors1]
;;       (let [[emap2 errors2] (run-callbacks etype hook emap1 remaining)]
;;         [emap2 (concat errors1 errors2)]))))

;; (defn- callbacks
;;   "Run all callbacks for a given combination emap, etype, & hook."
;;   [emap ereg etype hook]
;;   (if-let [fns (get-in ereg [etype :callbacks hook])]
;;     (->> (run-callbacks etype hook emap fns)
;;          (zipmap [:entity :errors]))
;;     {:entity emap :errors []}))

;; ;; 5. internal helpers

;; (defn- if-call
;;   "If pred of value is true then return f of value, otherwise value."
;;   [value pred f]
;;   (if (pred value) (f value) value))

;; (defn- report-errors [ctx action result]
;;   (log/warn "A callback prevented" ctx "from being" action ":" (:errors result))
;;   (assoc result :success false))

;; (defn- generic-persist [f table emap]
;;   ;; TODO maybe move assertation to meta :pre
;;   (assert (string? table) "table must be a string")
;;   (f {:id (:id emap)
;;       :table table
;;       :keys (map name (keys emap))
;;       :values (vals emap)}))

;; ;; 6. retrieving and persisting functions

;; ;; etype ("entity type") is a keyword that was previously registered
;; ;; with `register`

;; ;; TODO add a callback to use for authorization
;; ;; TODO rename to get
;; (defn rget*
;;   "Get an entity by id. Takes either an id or a hashmap with an :id
;;   entry."
;;   [ereg etype id-or-emap]
;;   (let [get-fn (get-in ereg [etype :get-fn] db/get-entity)
;;         id (if-call id-or-emap map? :id)
;;         table (get-in ereg [etype :tablename])]
;;     ;;(log/info table id get-fn)
;;     (if-let [emap (first (get-fn {:table table :id id}))]
;;       (map->EntityMap emap))))

;; ;; TODO add a callback to use for authorization
;; (defn find*
;;   "Find entities by conditions. The conditions are directly passed to
;;   the :find-fn. Since the underlying HugSQL uses sqlvecs, this must be
;;   an sqlvec."
;;   ([ereg etype conditions]
;;    (let [find-fn (get-in ereg [etype :find-fn] db/find-entities)
;;          table (get-in ereg [etype :tablename])]
;;      (map map->EntityMap (find-fn {:table table :conditions conditions}))))
;;   ([ereg etype]
;;    (find* etype ["1=1"])))

;; (defn create* [ereg etype emap]
;;   (let [table (get-in ereg [etype :tablename])
;;         create-fn (get-in ereg [etype :create-fn]
;;                           (partial generic-persist db/create-entity table))
;;         result (callbacks emap ereg etype :create)]
;;     (if (empty? (:errors result))
;;       (-> (create-fn (:entity result))
;;           (callbacks ereg etype :created)
;;           ;; NOTE `:affected 1` is only to be API-compatible to
;;           ;; what we had already
;;           (assoc :success true :affected 1)
;;           (clojure.core/update :entity map->EntityMap))
;;       (report-errors (name etype) "created" result))))

;; (defn- rget-helper
;;   "Reverses the arguments so it can be used in a thread first."
;;   [emap etype ereg]
;;   (rget* ereg etype emap))

;; (defn update* [ereg etype emap]
;;   (let [table (get-in ereg [etype :tablename])
;;         update-fn (get-in ereg [etype :update-fn]
;;                           (partial generic-persist db/update-entity table))
;;         result (callbacks emap ereg etype :update)]
;;     (if (empty? (:errors result))
;;       (let [affected (update-fn (:entity result))]
;;         (-> (:entity result)
;;             (rget-helper etype ereg)
;;             (callbacks ereg etype :updated)
;;             (assoc :success true :affected affected)
;;             (clojure.core/update :entity map->EntityMap)))
;;       (report-errors (name etype) "updated" result))))

;; (defn destroy* [ereg etype emap-or-id]
;;   (let [table (get-in ereg [etype :tablename])
;;         destroy-fn (get-in ereg [etype :destroy-fn]
;;                            (partial db/delete-entity table))
;;         emap1 (if (map? emap-or-id) emap-or-id {:id emap-or-id})
;;         result (callbacks emap1 ereg etype :destroy)]
;;     (if (empty? (:errors result))
;;       (let [affected (destroy-fn (:entity result))]
;;         (-> (:entity result)
;;             (callbacks ereg etype :destroyed)
;;             (assoc :success true :affected affected)
;;             (clojure.core/update :entity map->EntityMap)))
;;      (report-errors (name etype) "destroyed" result))))
