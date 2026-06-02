(ns acme.content.markdown-spec
  (:require [acme.content.markdown :as sut]
            [speclj.core :refer [describe it should-be-nil should=]]))

;; nj/markdown wraps single-element output in a `[:div ...]` envelope.
;; `acme.content.markdown/->hiccup` strips the envelope when there is exactly
;; one child, returning just that element; otherwise it returns the `:div`
;; wrapper as-is so multi-element output stays render-safe.
;;
;; Heading nodes carry a slugified `:id` attribute (`{:id "header"}` etc.),
;; which is correct semantic HTML and useful for anchor links in rendered
;; content.

(describe "Markdown ->hiccup (nj/markdown backend)"

  (it "empty string"
    (should-be-nil (sut/->hiccup "")))

  (it "nil input"
    (should-be-nil (sut/->hiccup nil)))

  (it "headings carry slugified :id"
    (should= [:h1 {:id "header"} "Header"] (sut/->hiccup "# Header"))
    (should= [:h2 {:id "header"} "Header"] (sut/->hiccup "## Header"))
    (should= [:h3 {:id "header"} "Header"] (sut/->hiccup "### Header"))
    (should= [:h4 {:id "header"} "Header"] (sut/->hiccup "#### Header"))
    (should= [:h5 {:id "header"} "Header"] (sut/->hiccup "##### Header"))
    (should= [:h6 {:id "header"} "Header"] (sut/->hiccup "###### Header")))

  (it "paragraph"
    (should= [:p "para"] (sut/->hiccup "para")))

  (it "tight unordered list — list items are not wrapped in :p"
    ;; nj/markdown wraps :li children in a list to support multi-element
    ;; bodies uniformly; that's render-equivalent to a vector for hiccup.
    (should= [:ul [:li '("1")] [:li '("2")]]
             (sut/->hiccup "* 1\n* 2")))

  (it "tight ordered list — has :start attribute"
    (should= [:ol {:start 1} [:li '("1")] [:li '("2")]]
             (sut/->hiccup "1. 1\n2. 2")))

  (it "inline code"
    (should= [:p [:code "blah"]] (sut/->hiccup "`blah`")))

  (it "blockquote"
    (should= [:blockquote [:p "foo"]] (sut/->hiccup "\n>  foo\n")))

  (it "indented code block"
    (should= [:pre [:code "this is code\n"]]
             (sut/->hiccup "\n    this is code\n")))

  (it "fenced code block — uses :code.language-X class shorthand"
    (should= [:pre [:code.language-gibberish "this is code\n"]]
             (sut/->hiccup "```gibberish\nthis is code\n```")))

  (it "link"
    (should= [:p [:a {:href "url"} "foo"]] (sut/->hiccup "[foo](url)")))

  (it "image"
    (should= [:p [:img {:src "src" :title "title" :alt "alt"}]]
             (sut/->hiccup "![alt](src \"title\")")))

  (it "em"
    (should= [:p [:em "blah"]] (sut/->hiccup "*blah*")))

  (it "strong"
    (should= [:p [:strong "blah"]] (sut/->hiccup "**blah**")))

  (it "strong em"
    (should= [:p [:em [:strong "blah"]]] (sut/->hiccup "***blah***")))

  (it "hr"
    (should= [:hr] (sut/->hiccup "---")))

  (it "soft break — passes through as space"
    (should= [:p "foo" " " "bar"] (sut/->hiccup "foo\nbar")))

  (it "hard break — emits :br"
    (should= [:p "foo" [:br] "bar"] (sut/->hiccup "foo  \nbar")))

  (it "inline HTML — text passes through verbatim"
    (should= [:p "<s>" "bold" "</s>"] (sut/->hiccup "<s>bold</s>")))

  (it "block HTML — text passes through verbatim"
    (should= "<s>\nbold\n</s>" (sut/->hiccup "<s>\nbold\n</s>")))

  (it "GFM table — header cells are :th, body cells :td"
    (should= [:table
              [:thead [:tr [:th "header 1"] [:th "header 2"]]]
              [:tbody [:tr [:td "cell 1"] [:td "cell 2"]]]]
             (sut/->hiccup (str "| header 1 | header 2 |\n"
                                "| -------- | -------- |\n"
                                "| cell 1   | cell 2   |\n"))))

  (it "strikethrough"
    (should= [:p [:s "strike"]] (sut/->hiccup "~~strike~~")))

  ;; Authors can drop a hiccup-shaped vector on its own line in markdown to
  ;; mark a slot for a client-side component:
  ;;   [:quote-block {:text "…"}]
  ;; The pipeline rewrites that paragraph to the literal hiccup vector so
  ;; `hiccup-registry/resolve-components` can swap it for a reagent fn.

  (it "[:keyword {…}] paragraph — parsed as hiccup vector"
    (should= [:quote-block {:text "hi"}]
             (sut/->hiccup "[:quote-block {:text \"hi\"}]")))

  (it "[:keyword {…}] paragraph mixed with prose"
    (should= [:div
              [:p "before"]
              [:quote-block {:text "hi" :n 1}]
              [:p "after"]]
             (sut/->hiccup
              "before\n\n[:quote-block {:text \"hi\" :n 1}]\n\nafter")))

  (it "[:keyword] without props"
    (should= [:divider]
             (sut/->hiccup "[:divider]")))

  (it "non-vector bracket text — left alone"
    (should= [:p "[just text]"]
             (sut/->hiccup "[just text]")))

  (it "malformed EDN — left as paragraph text"
    (should= [:p "[:quote-block {:text"]
             (sut/->hiccup "[:quote-block {:text")))

  (it "inline `[:tag …]` inside a paragraph — left as inline code"
    (should= [:p "see " [:code "[:quote-block {:x 1}]"] " here"]
             (sut/->hiccup "see `[:quote-block {:x 1}]` here"))))
