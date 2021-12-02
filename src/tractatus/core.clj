(ns tractatus.core
  (:require [tractatus
             [protocols :as tp]
             [inflection :as ti]]
            [clojure.string :as str]))

;;; General Helpers

(defn assert-fn
  "Asserts that F is a function and returns f for threadability"
  [f]
  (assert (fn? f) (str "Expected function, found " (or (type f) "nil") "."))
  f)

(defn new*
  "Slow instanciation without the use of any special forms."
  [klass & args]
  (clojure.lang.Reflector/invokeConstructor klass (to-array args)))

(defn- arity
  "Returns the arity of a function F"
  [f]
  {:pre [(instance? clojure.lang.AFunction f)]}
  (-> f class .getDeclaredMethods first .getParameterTypes alength))

(defn make-tablename
  "Takes a class C and returns a somewhat meaningful tablename"
  [c]
  (-> (.getName c)
      (str/split #"\.")
      last
      ;; poor man's Pascal-/camel-case to snake-case
      (str/replace #"([a-z])([A-Z])" "$1_$2")
      str/lower-case
      ti/pluralize))

(defn- ref-attr
  "The default ref-attr builder. Takes a keyword and returns a
  keyword. E.g. `:things` -> `:thing_ids`."
  [attr-name]
  (keyword (str (ti/singularize (name attr-name)) "_ids")))

(defn- apply-association [attrs [assoc-name assoc-aspects]]
  (-> attrs
      (assoc (or (:ref-attr assoc-aspects) (ref-attr assoc-name))
             (map (or (:foreign-key assoc-aspects) :id) (assoc-name attrs)))
      (dissoc assoc-name)))

(defn- apply-associations [assocs attrs]
  (reduce apply-association attrs assocs))

;;; Callback Helpers

(defn- run-callback
  "Runs the given callback and interprets the result. Returns a
  vector (tuple) of the potentially modified attributes map and a
  vector of error maps."
  [callback record hook]
  (let [result (case (arity callback)
                 2 (callback record hook)
                 1 (callback record)
                 (throw (ex-info
                         "Wrong arity for callback"
                         {:resource (name (type record))
                          :hook hook})))]
    (cond
      ;; happy cases
      (true? result) [record []]
      (instance? (type record) result) [result []]
      ;; unhappy cases
      (false? result) [record [{:error "Unspecified Error"}]]
      (string? result) [record [{:error result}]]
      (sequential? result) [record result]
      :default [record [{:error "Unknown Error" :object result}]])))

(defn run-callbacks [record hook fns]
  (let [[callback & remaining] fns
        [record¹ errors¹] (run-callback callback record hook)]
    (if (or (empty? remaining) (some :halt errors¹))
      [record¹ errors¹]
      (let [[record² errors²] (run-callbacks record hook remaining)]
        [record² (concat errors¹ errors²)]))))

;;; Defaults

;; TODO: use qualified keywords instead
(def default-aspects
  {:primary-key :id
   :retrievable
   {:find-by-id 'tractatus.persistence/find-by-id
    :find-by-conditions 'tractatus.persistence/find-by-conditions}
   :persistable
   {:insert! 'tractatus.persistence/insert!
    :update! 'tractatus.persistence/update!
    :delete! 'tractatus.persistence/delete!}})

;;; defresource

(defmacro defresource [resource-name aspects]
  ;; TODO: make take a form and make the form transform the aspects, similar to
  ;;
  ;; (defmacro defdomain
  ;;   [domain-name & body]
  ;;   `(def ~domain-name
  ;;      (-> base-domain
  ;;          ~@body
  ;;          ;; (validate ::domain)
  ;;          ;; TODO: use resulting data structure to setup resource records
  ;;          )))
  `(deftype ~resource-name [~'attrs]

     ;; using the fully qualified protocols here for the
     ;; sake of clarity

     java.lang.Iterable ; -----------------------------------------------

     ;; TODO: implement
     ;; (forEach [this#]
     ;;   this#)

     (iterator [this#]
       (-> this# .seq clojure.lang.SeqIterator.))

     ;; TODO: implement
     ;; (spliterator [this#]
     ;;   this#)

     java.lang.Object ; -------------------------------------------------

     (hashCode [this#]
       (reduce hash-combine
               (.hashCode (keyword (str *ns*) (.getName ~resource-name)))
               ~'attrs))

     (equals [this# other#]
       (boolean (or (identical? this# other#)
                    (when (identical? (class this#) (class other#))
                      (= ~'attrs (.attrs other#))))))

     java.lang.Runnable ; -----------------------------------------------

     ;; TODO: implement
     ;; (run [#this]
     ;;   #this)

     java.util.concurrent.Callable ; ------------------------------------

     ;; TODO: implement
     ;; (call [#this]
     ;;   #this)

     ;; java.util.Map ; ----------------------------------------------------

     ;; TODO: implement a ton of methods

     clojure.lang.Associative ; -----------------------------------------

     (containsKey [this# key#]
       (contains? ~'attrs key#))

     (entryAt [this# key#]
       (get ~'attrs key#))

     (assoc [this# key# val#]
       (new* (type this#) (assoc ~'attrs key# val#)))

     clojure.lang.Counted ; ---------------------------------------------

     (count [this#]
       (count ~'attrs))

     clojure.lang.IFn ; -------------------------------------------------

     (invoke [this# arg#]
       (clojure.lang.ILookup/.valAt this# arg#))

     ;; clojure.lang.IKeywordLookup ; --------------------------------------
     ;;
     ;; (getLookupThunk [this# key#]
     ;;   (get ~'attrs key#))

     clojure.lang.ILookup ; ---------------------------------------------

     (valAt [this# key#]
       (get ~'attrs key#
            ;; TODO: maybe fallback to retrieve associated
            ;; fallback to lookup in aspects
            (get (tp/aspects this#) key#)))

     (valAt [this# key# default#]
       (get ~'attrs key#
            ;; TODO: maybe fallback to retrieve associated
            ;; fallback to lookup in aspects
            (get (tp/aspects this#) key# default#)))

     ;; TODO: add
     ;; clojure.lang.IObj ; ------------------------------------------------

     clojure.lang.IPersistentCollection ; -------------------------------

     ;; TODO: implement
     ;; (cons [this# arg#]
     ;;    this#)

     ;; (count [this#]
     ;;   (count ~'attrs))

     (empty [this#]
       (new* (type this#) {}))

     (equiv [this# arg#]
       (.equals this# arg#))

     clojure.lang.IPersistentMap ; --------------------------------------

     ;; (count [this] (.count __map))

     ;; (empty [this] (DefaultMap. default (.empty __map)))

     ;; (cons [this e] (DefaultMap. default (.cons __map e)))

     ;; (equiv [this o] (.equals this o))

     ;; (containsKey [this k] true)

     ;; (entryAt [this k] (.entryAt __map k))

     ;; (seq [this#]
     ;;   (.seq ~'attrs))

     ;; (assoc [this k v]
     ;;   (DefaultMap. default (.assoc __map k v)))

     (without [this# key#]
       (new* (type this#) (.without ~'attrs key#)))

     clojure.lang.Seqable ; ---------------------------------------------

     (seq [this#]
       (.seq ~'attrs))

     ;; our own stuff follows ===========================================

     tractatus.protocols.IAspects ; -------------------------------------

     (aspects [this#]
       ;; TODO: deep merge
       (merge default-aspects
              {:tablename (make-tablename ~resource-name)}
              ~aspects))

     tractatus.protocols.IAttributes ; ----------------------------------

     (attributes [this#]
       ~'attrs)

     tractatus.protocols/Retrievable ; ----------------------------------

     (find-by-id [this# id#]
       (assert (-> this# tp/aspects :datasource) "Oops, no datasource?")
       (if-let [attrs#
                ((get (tp/aspects this#) :transform-on-read identity)
                 ((-> this# tp/aspects :retrievable :find-by-id resolve deref assert-fn)
                  (-> this# tp/aspects :datasource)
                  (select-keys (tp/aspects this#) [:tablename :primary-key])
                  id#))]
         (new* (type this#) attrs#)
         nil)) ; if not found return nil

     (find-by-conditions [this# conditions#]
       (assert (-> this# tp/aspects :datasource) "Oops, no datasource?")
       (not-empty ; if not found instead of an empty list return nil
        (map
         (comp
          (partial new* (type this#))
          (get (tp/aspects this#) :transform-on-read identity))
         ((-> this# tp/aspects :retrievable :find-by-conditions resolve deref assert-fn)
          (-> this# tp/aspects :datasource)
          (select-keys (tp/aspects this#) [:tablename :primary-key])
          conditions#))))

     tractatus.protocols/Persistable ; ----------------------------------

     (insert! [this#]
       (assert (-> this# tp/aspects :datasource) "Oops, no datasource?")
       (new* (type this#)
             ((get (tp/aspects this#) :transform-on-read identity)
              ((-> this# tp/aspects :persistable :insert! resolve deref assert-fn)
               (-> this# tp/aspects :datasource)
               (select-keys (tp/aspects this#) [:tablename :primary-key])
               ((get (tp/aspects this#) :transform-on-write identity)
                (apply-associations (-> this# tp/aspects :associations) ~'attrs))))))

     (update! [this#]
       (assert (-> this# tp/aspects :datasource) "Oops, no datasource?")
       (new* (type this#)
             ((get (tp/aspects this#) :transform-on-read identity)
              ((-> this# tp/aspects :persistable :update! resolve deref assert-fn)
               (-> this# tp/aspects :datasource)
               (select-keys (tp/aspects this#) [:tablename :primary-key])
               ((get (tp/aspects this#) :transform-on-write identity) ~'attrs)))))

     (delete! [this#]
       (assert (-> this# tp/aspects :datasource) "Oops, no datasource?")
       (new* (type this#)
             ((get (tp/aspects this#) :transform-on-read identity)
              ((-> this# tp/aspects :persistable :delete! resolve deref assert-fn)
               (-> this# tp/aspects :datasource)
               (select-keys (tp/aspects this#) [:tablename :primary-key])
               ((-> this# tp/aspects :primary-key) ~'attrs)))))

     tractatus.protocols.Callbackable ; ---------------------------------

     (callbacks [this# hook#]
       (if-let [fns# (get-in (tp/aspects this#) [:callbacks hook#])]
         (->> (run-callbacks this# hook# fns#)
              (zipmap [:record :errors]))
         {:record this# :errors []}))

     tractatus.protocols.Lifecyclable ; ---------------------------------

     (create! [this#]
       (let [result¹# (tp/callbacks this# :create)]
         (if (-> result¹# :errors empty?)
           (let [result²# (tp/callbacks (tp/insert! (:record result¹#)) :created)]
             (if (-> result²# :errors empty?)
               (:record result²#)
               ;; TODO: rollback
               (throw (ex-info "Error in callback after create w/o rollback" result²#))))
           (throw (ex-info "Failed to create" result¹#)))))

     (modify! [this#]
       (let [result¹# (tp/callbacks this# :modify)]
         (if (-> result¹# :errors empty?)
           (let [result²# (tp/callbacks (tp/update! (:record result¹#)) :modified)]
             (if (-> result²# :errors empty?)
               (:record result²#)
               ;; TODO: rollback
               (throw (ex-info "Error in callback after modify w/o rollback" result²#))))
           (throw (ex-info "Failed to modify" result¹#)))))

     (destroy! [this#]
       (let [result¹# (tp/callbacks this# :destroy)]
         (if (-> result¹# :errors empty?)
           (let [result²# (tp/callbacks (tp/delete! (:record result¹#)) :destroyed)]
             (if (-> result²# :errors empty?)
               (:record result²#)
               ;; TODO: rollback
               (throw (ex-info "Error in callback after destroy w/o rollback" result²#))))
           (throw (ex-info "Failed to destroy" result¹#)))))

     ;; tractatus.protocols.IInspect ; -------------------------------------
     ;; (inspect [this#]
     ;;   (new* (type this#) {}))

     ))

;;; Delegation and syntactic sugar

(def aspects tp/aspects)

(def attributes tp/attributes)

(defn find-by-id [resource-class id]
  (tp/find-by-id (new* resource-class {}) id))

(defn find-by-conditions [resource-class conditions]
  (tp/find-by-conditions (new* resource-class {}) conditions))

(def insert! tp/insert!)

(def update! tp/update!)

(defn delete!
  ([resource]
   (tp/delete! resource))
  ([resource-class id]
   (let [{:keys [primary-id]} (aspects (new* resource-class {}))]
     (delete! (new* resource-class {primary-id id})))))

(def create! tp/create!)

(def modify! tp/modify!)

(defn destroy!
  ([resource]
   (tp/destroy! resource))
  ([resource-class id]
   (let [{:keys [primary-id]} (aspects (new* resource-class {}))]
     (destroy! (new* resource-class {primary-id id})))))

;;; Notes

(comment

  (require '[tractatus.persistence.atom :as ta])

  (def db (ta/make-atomdb))

  (defresource Monkey
    {:datasource db
     ::name :monkey})
  ;; create instance
  (def monkey (->Monkey {:name "Bubbles" :banana true}))
  ;; lookup attributes
  (:banana monkey)
  (monkey :banana)
  (monkey :name)
  (:name monkey)
  ;; lookup spec data
  (::name monkey)
  (monkey ::name)

  (assoc monkey :name "Donkey Kong")
  (assoc monkey
         :name "Donkey Kong"
         :age 5)
  ((assoc monkey :name "Donkey Kong") :name)

  (def saved-monkey (create! monkey))
  (:id saved-monkey)
  (attributes monkey)
  (attributes saved-monkey)
  (find-by-id Monkey (:id saved-monkey))
  (find-by-conditions Monkey {:name "Bubbles"})

  ;; https://gist.github.com/michalmarczyk/468332
  (deftype DefaultMap [default __map]
    Object
    (hashCode [this] (reduce hash-combine
                             (keyword (str *ns*) "DefaultMap")
                             default
                             __map))
    (equals [this other]
      (boolean (or (identical? this other)
                   (when (identical? (class this) (class other))
                     (every? true? (map = [default __map]
                                        [(.default other)
                                         (.__map other)]))))))
    clojure.lang.IObj
    (meta [this] (.meta __map))
    (withMeta [this m]
      (DefaultMap. default (.withMeta __map m)))
    clojure.lang.ILookup
    (valAt [this k] (.valAt __map k default))
    (valAt [this k else] (.valAt __map k else))
    clojure.lang.IKeywordLookup
    (getLookupThunk [this k]
      (reify clojure.lang.ILookupThunk
        (get [thunk target]
          (if (identical? (class target) (class this))
            (.valAt this k)))))
    clojure.lang.IPersistentMap
    (count [this] (.count __map))
    (empty [this] (DefaultMap. default (.empty __map)))
    (cons [this e] (DefaultMap. default (.cons __map e)))
    (equiv [this o] (.equals this o))
    (containsKey [this k] true)
    (entryAt [this k] (.entryAt __map k))
    (seq [this] (.seq __map))
    (assoc [this k v]
      (DefaultMap. default (.assoc __map k v)))
    (without [this k]
      (DefaultMap. default (.without __map k)))

    java.lang.Iterable
    (iterator [this] (-> this .seq SeqIterator.))
    )

  (keys (->DefaultMap 42 {:a 23}))

  ;; https://stackoverflow.com/questions/9225948/how-do-turn-a-java-iterator-like-object-into-a-clojure-sequence
  (into {} (->DefaultMap 42 {:a 23}))
  (bean (->DefaultMap 42 {:a 23}))

  ;; TODO: https://gist.github.com/kriyative/2642569

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
