(ns tractatus.resources
  (:require [camel-snake-kebab.core :as csk]))

;;; Protocols

(defprotocol Describable
  (describe [this]))

(defprotocol Retrievable
  (find-by-id [this id])
  (find-by-conditions [this conditions]))

(defprotocol Persistable
  "Low level fns which operate directly on the persistence layer."
  (insert! [this attrs])
  (update! [this attrs])
  (delete! [this id]))

(defprotocol Callbackable
  "Run all callbacks on ATTRS for a given HOOK."
  (callbacks [this attrs hook]))

(defprotocol Lifecyclable
  "Higher level fns which handle callbacks and such."
  (create! [this attrs])
  (modify! [this attrs])
  (destroy! [this id]))

;;; General Helpers

(defn- assert-fn
  "Asserts that F is a function and returns f for threadability"
  [f]
  (assert (fn? f) (str "Expected function, found " (or (type f) "nil") "."))
  f)

;;; Callback Helpers

(defn- arity [f]
  {:pre [(instance? clojure.lang.AFunction f)]}
  (-> f class .getDeclaredMethods first .getParameterTypes alength))

(defn- run-callback
  "Runs the given callback and interprets the result. Returns a
  vector (tuple) of the potentially modified attributes map and a
  vector of error maps."
  [callback resource hook attrs]
  (let [result (case (arity callback)
                 3 (callback attrs resource hook)
                 2 (callback attrs resource)
                 1 (callback attrs)
                 (throw (ex-info
                         "Wrong arity for callback"
                         {:resource (:name resource)
                          :hook hook})))]
    (cond
      ;; happy cases
      (true? result) [attrs []]
      (map? result) [result []]
      ;; unhappy cases
      (false? result) [attrs [{:error "Unspecified Error"}]]
      (string? result) [attrs [{:error result}]]
      (sequential? result) [attrs result]
      :default [attrs [{:error "Unknown Error" :object result}]])))

(defn- run-callbacks [resource hook attrs fns]
  (let [[callback & remaining] fns
        [attrs¹ errors¹] (run-callback callback resource hook attrs)]
    (if (or (empty? remaining) (some :halt errors¹))
      [attrs¹ errors¹]
      (let [[attrs² errors²] (run-callbacks resource hook attrs¹ remaining)]
        [attrs² (concat errors¹ errors²)]))))

;;; Resource (type)

(deftype Resource [specification]

  Describable
  (describe [this]
    specification)

  Retrievable
  (find-by-id [this id]
    ((-> specification :retrievable :find-by-id resolve deref assert-fn)
     (-> specification :datasource)
     (select-keys specification [:tablename :primary-key])
     id))
  (find-by-conditions [this conditions]
    ((-> specification :retrievable :find-by-conditions resolve deref assert-fn)
     (-> specification :datasource)
     (select-keys specification [:tablename :primary-key])
     conditions))

  Persistable
  (insert! [this attrs]
    ((-> specification :persistable :insert! resolve deref assert-fn)
     (-> specification :datasource)
     (select-keys specification [:tablename :primary-key])
     attrs))
  (update! [this attrs]
    ((-> specification :persistable :update! resolve deref assert-fn)
     (-> specification :datasource)
     (select-keys specification [:tablename :primary-key])
     attrs))
  (delete! [this id]
    ((-> specification :persistable :delete! resolve deref assert-fn)
     (-> specification :datasource)
     (select-keys specification [:tablename :primary-key])
     id))

  Callbackable
  (callbacks [this attrs hook]
    (if-let [fns (get-in specification [:callbacks hook])]
      (->> (run-callbacks this hook attrs fns)
           (zipmap [:attrs :errors]))
      {:attrs attrs :errors []}))

  Lifecyclable
  (create! [this attrs]
    (let [{:keys [attrs errors]} (callbacks this attrs :create)]
      (if (empty? errors)
        (callbacks this (insert! this attrs) :created)
        (throw (ex-info "Failed to create" {:errors errors :attrs attrs})))))
  (modify! [this attrs]
    (let [{:keys [attrs errors]} (callbacks this attrs :modify)]
      (if (empty? errors)
        (callbacks this (update! this attrs) :modified)
        (throw (ex-info "Failed to modify" {:errors errors :attrs attrs})))))
  (destroy! [this attrs]
    (let [{:keys [attrs errors]} (callbacks this attrs :destroy)
          id (get attrs (:primary-id specification))]
      (if (empty? errors)
        (callbacks this (delete! this id) :destroyed)
        (throw (ex-info "Failed to destroy" {:errors errors :attrs attrs}))))))

;;; Reify Domain

(defn reify-domain
  "Takes a domain and defines the specified resources in the namespace
  it is called from. See https://stackoverflow.com/a/68379190/3094876"
  [{:keys [resources]}]
  (doseq [[resource-name resource] resources]
    (let [resource-symbol (symbol (csk/->PascalCase resource-name))]
      (intern *ns* resource-symbol (->Resource resource)))))

;;; Notes

(comment

  ;; the application can use it like this

  (def ^:private ident (fn [& args] args))

  (def ^:private employee-details
    {:tablename :employee
     :retrievable {:find-by-id ident
                   :find-by-conditions ident}})

  (def ^:private domain
    {:resources {:employee employee-details}})

  ;; TODO: use the domain dsl instead

  (reify-domain domain)

  (find-by-id employee 1)

  (find-by-conditions employee {:name "Phil"})

  (describe employee)
  )


(comment
  (defmacro mkfn [fns]
    `(do
       ~@(for [fn# fns]
           `(defn ~(symbol fn#) []
              ~fn#))))

  (macroexpand '(mkfn [:a]))

  (mkfn [:a :b :c])

  (a)
  (b)
  (c)
  )
