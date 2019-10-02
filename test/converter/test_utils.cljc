(ns converter.test-utils
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.test.check.generators]
   [converter.app :as app]
   [converter.config :as config]
   [converter.time :as time]
   [converter.rekordbox.core :as r]
   [converter.spec :as spec]
   [converter.traktor.core :as t]
   [converter.universal.core :as u]
   [converter.universal.marker :as um]
   [converter.universal.tempo :as ut]
   [converter.xml :as xml]
   [spec-tools.core :as st]
   [tick.alpha.api :as tick]))

; TODO move to universal.core ns
(defn- library-items-map
  [library item-map-fn]
  (if (::u/collection library)
    (update library ::u/collection #(map item-map-fn %))))

; TODO move to universal.core ns
(defn- library-items-filter
  [library item-filter-fn]
  (if (::u/collection library)
    (update library ::u/collection #(filter item-filter-fn %))))

(defn- item-markers-remove-hidden-markers-with-matching-non-hidden-marker
  [item]
  (if (::u/markers item)
    (update item ::u/markers #(concat (um/visible-markers %) (um/hidden-markers-without-matching-visible-marker %)))
    item))

(defn- item-tempos-dissoc-bpm-metro-battito
  [item]
  (if (::u/tempos item)
    (update item ::u/tempos (fn [tempos] (mapv #(dissoc % ::ut/bpm ::ut/metro ::ut/battito) tempos)))
    item))

(defn library-equiv-traktor
  "Returns a library that is expected to be equivalent with the given library, after it has been converted to Traktor data and back again"
  [library]
  ; TODO for the first tempo of each item, assert bpm's are equal (in addition to inizio being equal)
  ((comp
    #(library-items-map % u/sorted-tempos)
    #(library-items-map % item-tempos-dissoc-bpm-metro-battito)
    #(library-items-map % u/sorted-markers)
    #(library-items-map % item-markers-remove-hidden-markers-with-matching-non-hidden-marker))
   library))

(defn- marker-type-supported?
  [marker-type]
  (contains? #{::um/type-cue ::um/type-loop} marker-type))

(defn- item-markers-unsupported-type->cue-type
  [item]
  (if (::u/markers item)
    (update item ::u/markers (fn [markers] (mapv #(if (marker-type-supported? %)
                                                    %
                                                    (assoc % ::um/type ::um/type-cue)) markers)))
    item))

(defn library-equiv-rekordbox
  "Returns a library that is expected to be equivalent with the given library, after it has been converted to Rekordbox data and back again"
  [library]
  ((comp 
    #(library-items-map % item-markers-unsupported-type->cue-type)
    #(library-items-filter % u/item-contains-total-time?)) 
   library))

(def config
  (gen/generate (s/gen config/config-spec)))

(defn traktor-round-trip
  [config library]
  (as-> library $
    (st/encode (t/nml-spec config) $ spec/xml-transformer)
    (xml/encode $)
    (xml/decode $)
    (spec/decode! (t/nml-spec config) $ spec/string-transformer)
    (spec/decode! t/library-spec $ spec/xml-transformer)))

(defn rekordbox-round-trip
  [config library]
  (as-> library $
    (st/encode (r/dj-playlists-spec config) $ spec/xml-transformer)
    (xml/encode $)
    (xml/decode $)
    (spec/decode! (r/dj-playlists-spec config) $ spec/string-transformer)
    (spec/decode! r/library-spec $ spec/xml-transformer)))
