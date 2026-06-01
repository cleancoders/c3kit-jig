(ns acme.styles.main
  (:require [garden.def :as garden]
            [acme.styles.core :as core]
            [acme.styles.components.menus :as menus]
            [acme.styles.elements.forms :as forms]
            [acme.styles.elements.lists :as lists]
            [acme.styles.elements.media :as media]
            [acme.styles.elements.tables :as tables]
            [acme.styles.elements.typography :as typography]
            [acme.styles.layout.document :as document]
            [acme.styles.layout.mini-classes :as mini-classes]
            [acme.styles.layout.page :as page]
            [acme.styles.layout.reset :as reset]
            [acme.styles.layout.structure :as structure]
            [acme.styles.media.responsive :as responsive]
            [acme.styles.pages.authentication :as authentication]))

(garden/defstyles screen

; Layout
  reset/screen
  document/screen
  page/screen
  structure/screen
  mini-classes/screen

; Elements
  typography/screen
  forms/screen
  lists/screen
  media/screen
  tables/screen

; Componenents
  menus/screen

; Pages
  authentication/screen

; Media
  responsive/screen

; Fonts
  core/fonts)
