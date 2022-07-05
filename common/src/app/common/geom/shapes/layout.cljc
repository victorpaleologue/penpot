;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) UXBOX Labs SL

(ns app.common.geom.shapes.layout
  (:require
   [app.common.geom.matrix :as gmt]
   [app.common.geom.point :as gpt]
   [app.common.geom.shapes.rect :as gre]
   [app.common.geom.shapes.transforms :as gtr]))

;; :layout                 ;; true if active, false if not
;; :layout-dir             ;; :right, :left, :top, :bottom
;; :layout-gap             ;; number could be negative
;; :layout-type            ;; :packed, :space-between, :space-around
;; :layout-wrap-type       ;; :wrap, :no-wrap
;; :layout-padding-type    ;; :simple, :multiple
;; :layout-padding         ;; {:p1 num :p2 num :p3 num :p4 num} number could be negative
;; :layout-h-orientation   ;; :top, :center, :bottom
;; :layout-v-orientation   ;; :left, :center, :right

(defn col?
  [{:keys [layout-dir]}]
  (or (= :right layout-dir) (= :left layout-dir)))

(defn row?
  [{:keys [layout-dir]}]
  (or (= :top layout-dir) (= :bottom layout-dir)))

(defn h-start?
  [{:keys [layout-v-orientation]}]
  (= layout-v-orientation :left))

(defn h-center?
  [{:keys [layout-v-orientation]}]
  (= layout-v-orientation :center))

(defn h-end?
  [{:keys [layout-v-orientation]}]
  (= layout-v-orientation :right))

(defn v-start?
  [{:keys [layout-h-orientation]}]
  (= layout-h-orientation :top))

(defn v-center?
  [{:keys [layout-h-orientation]}]
  (= layout-h-orientation :center))

(defn v-end?
  [{:keys [layout-h-orientation]}]
  (= layout-h-orientation :bottom))

(defn add-padding [transformed-rect {:keys [layout-padding-type layout-padding]}]
  (let [{:keys [p1 p2 p3 p4]} layout-padding
        [p1 p2 p3 p4]
        (if (= layout-padding-type :multiple)
          [p1 p2 p3 p4]
          [p1 p1 p1 p1])]

    (-> transformed-rect
        (update :y + p1)
        (update :width - p2 p3)
        (update :x + p3)
        (update :height - p1 p4))))

(defn calc-layout-data
  "Digest the layout data to pass it to the constrains"
  [{:keys [layout-type layout-gap] :as shape} children modif-tree transformed-rect]

  (let [{:keys [x y width height]} (-> transformed-rect (add-padding shape))
        num-children (count children)

        [children-width children-height]
        (->> children
             (map #(-> (merge % (get modif-tree (:id %))) gtr/transform-shape))
             (reduce (fn [[acc-width acc-height] shape]
                       [(+ acc-width (-> shape :points gre/points->rect :width))
                        (+ acc-height (-> shape :points gre/points->rect :height))]) [0 0]))

        layout-gap
        (cond
          (= :packed layout-type)
          layout-gap

          (= :space-around layout-type)
          0

          (and (col? shape) (= :space-between layout-type))
          (/ (- width children-width) (dec num-children))

          (and (row? shape) (= :space-between layout-type))
          (/ (- height children-height) (dec num-children)))

        margin-x (if (and (col? shape) (= :space-around layout-type))
                   (/ (- width children-width) (dec num-children) 2)
                   0)

        margin-y (if (and (row? shape) (= :space-around layout-type))
                   (/ (- height children-height) (dec num-children) 2)
                   0)

        children-gap (* layout-gap (dec num-children))

        start-x
        (cond
          (or (and (col? shape) (= :space-between layout-type))
              (and (col? shape) (= :space-around layout-type)))
          x

          (and (row? shape) (h-center? shape))
          (+ x (/ width 2))

          (and (row? shape) (h-end? shape))
          (+ x width)

          (and (col? shape) (h-center? shape))
          (- (+ x (/ width 2)) (/ (+ children-width children-gap) 2))

          (and (col? shape) (h-end? shape))
          (- (+ x width) (+ children-width children-gap))

          :else
          x)

        start-y
        (cond
          (or (and (row? shape) (= :space-between layout-type))
              (and (row? shape) (= :space-around layout-type)))
          y

          (and (col? shape) (v-center? shape))
          (+ y (/ height 2))

          (and (col? shape) (v-end? shape))
          (+ y height)

          (and (row? shape) (v-center? shape))
          (- (+ y (/ height 2)) (/ (+ children-height children-gap) 2))

          (and (row? shape) (v-end? shape))
          (- (+ y height) (+ children-height children-gap))

          :else
          y)]

    {:start-x start-x
     :start-y start-y
     :layout-gap layout-gap
     :margin-x margin-x
     :margin-y margin-y
     :reverse? (or (= :left (:layout-dir shape)) (= :bottom (:layout-dir shape)))}))

(defn next-p
  "Calculates the position for the current shape given the layout-data context"
  [shape {:keys [width height]} {:keys [start-x start-y layout-gap margin-x margin-y] :as layout-data}]

  (let [pos-x
        (cond
          (and (row? shape) (h-center? shape))
          (- start-x (/ width 2))

          (and (row? shape) (h-end? shape))
          (- start-x width)

          :else
          start-x)

        pos-y
        (cond
          (and (col? shape) (v-center? shape))
          (- start-y (/ height 2))

          (and (col? shape) (v-end? shape))
          (- start-y height)

          :else
          start-y)

        pos-x (cond-> pos-x (some? margin-x) (+ margin-x))
        pos-y (cond-> pos-y (some? margin-y) (+ margin-y))

        corner-p (gpt/point pos-x pos-y)

        next-x
        (if (col? shape)
          (+ start-x width layout-gap)
          start-x)

        next-y
        (if (row? shape)
          (+ start-y height layout-gap)
          start-y)

        next-x (cond-> next-x (some? margin-x) (+ margin-x))
        next-y (cond-> next-y (some? margin-y) (+ margin-y))

        layout-data
        (assoc layout-data :start-x next-x :start-y next-y)]
    [corner-p layout-data])
  )

(defn calc-layout-modifiers
  "Calculates the modifiers for the layout"
  [parent child modifiers layout-data]

  (let [modifiers (-> modifiers (dissoc :displacement-after))
        child     (-> child (assoc :modifiers modifiers) gtr/transform-shape)
        bounds    (-> child :points gre/points->selrect)

        [corner-p layout-data] (next-p parent bounds layout-data)

        delta-p          (-> corner-p (gpt/subtract (gpt/point bounds)))
        modifiers        (-> modifiers (assoc :displacement-after (gmt/translate-matrix delta-p)))]

    [modifiers layout-data]))
