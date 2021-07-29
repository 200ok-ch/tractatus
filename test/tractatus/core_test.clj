(ns tractatus.core-test
  (:require [clojure.test :refer :all]
            [tractatus.core :as sut]
            ;; require all the other test ns
            tractatus.domain-test
            tractatus.persistence.atom-test
            tractatus.inflection-test
            tractatus.resources-test))
