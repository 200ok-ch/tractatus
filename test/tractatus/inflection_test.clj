(ns tractatus.inflection-test
  (:require [tractatus.inflection :as i]
            [clojure.test :refer :all]))

(deftest pluralize
  (are [singular plural] (= plural (i/pluralize singular))
    "rice"     "rice"
    "children" "children"
    "child"    "children"
    "quiz"     "quizzes"
    :quiz      :quizzes))

(deftest singularize
  (are [plural singular] (= singular (i/singularize plural))
    "rice"     "rice"
    "children" "child"
    "child"    "child"
    "quizzes"  "quiz"
    :quizzes   :quiz))
