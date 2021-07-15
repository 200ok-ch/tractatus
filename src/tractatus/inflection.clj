(ns tractatus.inflection
  "This is mainly a translation of parts of ActiveSupport's inflection library.

   See `https://github.com/rails/rails/blob/main/activesupport/lib/active_support/inflections.rb`"
  (:require [clojure.string :as str]
            [clojure.set :as set]))

(def ^:private uncountable
  "A set of uncountables."
  #{"equipment"
    "information"
    "rice"
    "money"
    "species"
    "series"
    "fish"
    "sheep"
    "jeans"
    "police"})

(def ^:private irregular
  "A map of irregular singular to plural."
  {"person" "people"
   "man"    "men"
   "child"  "children"
   "sex"    "sexes"
   "move"   "moves"
   "zombie" "zombies"})

(defn- irregular-singular->plural
  "Returns the plural of a irregular singular or nil."
  [s]
  (irregular s))

(defn- irregular-plural->plural
  "Returns `s` if `s` is an irregular plural."
  [s]
  ((-> irregular vals set) s))

(defn- irregular-plural->singular
  "Returns the singular of a irregular plural or nil."
  [s]
  ((-> irregular set/map-invert) s))

(defn- irregular-singular->singular
  "Returns `s` if `s` is an irregular singular."
  [s]
  ((-> irregular keys set) s))

(def ^:private plural-rules
  "A vector of pattern to replacement vector-tuples, which transform
  singulars to plurals."
  [[#"(quiz)$" "$1zes"]
   [#"^(oxen)$" "$1"]
   [#"^(ox)$" "$1en"]
   [#"^(m|l)ice$" "$1ice"]
   [#"^(m|l)ouse$" "$1ice"]
   [#"(matr|vert|ind)(?:ix|ex)$" "$1ices"]
   [#"(x|ch|ss|sh)$" "$1es"]
   [#"([^aeiouy]|qu)y$" "$1ies"]
   [#"(hive)$" "$1s"]
   [#"(?:([^f])fe|([lr])f)$" "$1$2ves"]
   [#"sis$" "ses"]
   [#"([ti])a$" "$1a"]
   [#"([ti])um$" "$1a"]
   [#"(buffal|tomat)o$" "$1oes"]
   [#"(bu)s$" "$1ses"]
   [#"(alias|status)$" "$1es"]
   [#"(octop|vir)i$" "$1i"]
   [#"(octop|vir)us$" "$1i"]
   [#"^(ax|test)is$" "$1es"]
   [#"s$" "s"]
   [#"$" "s"]])

(def ^:private singular-rules
  "A vector of pattern to replacement vector-tuples, which transform
  plurals to singulars."
  [[#"(database)s$" "$1"]
   [#"(quiz)zes$" "$1"]
   [#"(matr)ices$" "$1ix"]
   [#"(vert|ind)ices$" "$1ex"]
   [#"^(ox)en" "$1"]
   [#"(alias|status)(es)?$" "$1"]
   [#"(octop|vir)(us|i)$" "$1us"]
   [#"^(a)x[ie]s$" "$1xis"]
   [#"(cris|test)(is|es)$" "$1is"]
   [#"(shoe)s$" "$1"]
   [#"(o)es$" "$1"]
   [#"(bus)(es)?$" "$1"]
   [#"^(m|l)ice$" "$1ouse"]
   [#"(x|ch|ss|sh)es$" "$1"]
   [#"(m)ovies$" "$1ovie"]
   [#"(s)eries$" "$1eries"]
   [#"([^aeiouy]|qu)ies$" "$1y"]
   [#"([lr])ves$" "$1f"]
   [#"(tive)s$" "$1"]
   [#"(hive)s$" "$1"]
   [#"([^f])ves$" "$1fe"]
   [#"(^analy)(sis|ses)$" "$1sis"]
   [#"((a)naly|(b)a|(d)iagno|(p)arenthe|(p)rogno|(s)ynop|(t)he)(sis|ses)$" "$1sis"]
   [#"([ti])a$" "$1um"]
   [#"(n)ews$" "$1ews"]
   [#"(ss)$" "$1"]
   [#"s$" ""]])

(defn- apply-rules
  "Applies a given set of `rules` to a term `s` return a applicable
  replaced term or nil."
  [rules s]
  (loop [[[match replacement] & rules] rules]
    (if (re-find match s)
      (str/replace s match replacement)
      (when-not (empty? rules)
        (recur rules)))))

(defn pluralize
  "Takes a string or keyword and returns a pluralized term for a given term `s`."
  [s]
  (condp = (type s)
    clojure.lang.Keyword
    (-> s name pluralize keyword)
    java.lang.String
    (or
     (uncountable s)
     (irregular-singular->plural s)
     (irregular-plural->plural s)
     (apply-rules plural-rules s))
    (throw (ex-info (str "Unhandled type: " (type s))))))

(defn singularize
  "Takes a string or keyword and returns a singularized term for a given term `s`."
  [s]
  (condp = (type s)
    clojure.lang.Keyword
    (-> s name singularize keyword)
    java.lang.String
    (or
     (uncountable s)
     (irregular-plural->singular s)
     (irregular-singular->singular s)
     (apply-rules singular-rules s)
     ;; fallback to s itself
     s)
    (throw (ex-info (str "Unhandled type: " (type s))))))
