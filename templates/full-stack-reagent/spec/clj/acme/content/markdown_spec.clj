(ns acme.content.markdown-spec
  (:require [acme.content.markdown :as sut]
            [speclj.core :refer :all]))

(describe "Markdown"

  (it "empty string"
    (should-be-nil (sut/->hiccup "")))

  (it "headings"
    (should= [:h1 "Header"] (sut/->hiccup "# Header"))
    (should= [:h2 "Header"] (sut/->hiccup "## Header"))
    (should= [:h3 "Header"] (sut/->hiccup "### Header"))
    (should= [:h4 "Header"] (sut/->hiccup "#### Header"))
    (should= [:h5 "Header"] (sut/->hiccup "##### Header"))
    (should= [:h6 "Header"] (sut/->hiccup "###### Header")))

  (it "paragraph"
    (should= [:p "para"] (sut/->hiccup "para")))

  (it "unordered list"
    (should= [:ul [[:li [:p "1"]] [:li [:p "2"]]]] (sut/->hiccup " * 1\n * 2")))

  (it "ordered list"
    (should= [:ol [[:li [:p "1"]] [:li [:p "2"]]]] (sut/->hiccup " 1. 1\n 2. 2")))

  (it "code"
    (should= [:p [:code "blah"]] (sut/->hiccup "`blah`")))

  (it "blockquote"
    (should= [:blockquote [:p "foo"]] (sut/->hiccup "\n>  foo\n")))

  (it "indented clode block"
    (should= [:pre [:code "this is code\n"]] (sut/->hiccup "\n    this is code\n")))

  (it "fenced clode block"
    (should= [:pre [:code {:class "gibberish"} "this is code\n"]] (sut/->hiccup "```gibberish\nthis is code\n```")))

  (it "link"
    (should= [:p [:a {:href "url"} "foo"]] (sut/->hiccup "[foo](url)")))

  (it "image"
    (should= [:p [:img {:src "src" :alt "alt" :title "title"}]] (sut/->hiccup "![alt](src \"title\")")))

  (it "em"
    (should= [:p [:em "blah"]] (sut/->hiccup "*blah*")))

  (it "strong"
    (should= [:p [:strong "blah"]] (sut/->hiccup "**blah**")))

  (it "strong em"
    (should= [:p [:em [:strong "blah"]]] (sut/->hiccup "***blah***")))

  (it "hr"
    (should= [:hr] (sut/->hiccup "---")))

  (it "breaks"
    (should= [:p (list "foo" " " "bar")] (sut/->hiccup "foo\n bar"))
    (should= [:p (list "foo" [:br] "bar")] (sut/->hiccup "foo  \nbar")))

  (it "html inline"
    (should= [:p (list "<s>" "bold" "</s>")] (sut/->hiccup "<s>bold</s>")))

  (it "html block"
    (should= "<s>\nbold\n</s>" (sut/->hiccup "<s>\nbold\n</s>")))

  (it "tables"
    (should= [:table (list [:thead [:tr (list [:td "header 1"] [:td "header 2"])]]
                           [:tbody [:tr (list [:td "cell 1"] [:td "cell 2"])]])]
             (sut/->hiccup (str "| header 1 | header 2 |\n"
                                "| -------- | -------- |\n"
                                "| cell 1   | cell 2   |\n"))))

  (it "strikethrough"
    (should= [:p [:s "strike"]] (sut/->hiccup "~~strike~~")))

  (it "->html"
    (should= "<h1>Hi</h1>\n" (sut/->html "# Hi")))
  )
