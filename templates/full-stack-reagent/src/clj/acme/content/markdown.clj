(ns acme.content.markdown
  (:import (org.commonmark.ext.gfm.strikethrough Strikethrough StrikethroughExtension)
           (org.commonmark.ext.gfm.tables TableBlock TableBody TableCell TableHead TableRow TablesExtension)
           (org.commonmark.node BlockQuote BulletList Code Document Emphasis FencedCodeBlock HardLineBreak Heading HtmlBlock
                                HtmlInline Image IndentedCodeBlock Link ListItem OrderedList Paragraph SoftLineBreak
                                StrongEmphasis Text ThematicBreak)
           org.commonmark.parser.Parser
           (org.commonmark.renderer.html HtmlRenderer)))

(def table-extension (TablesExtension/create))
(def strikethrough-extension (StrikethroughExtension/create))

(def parser (-> (Parser/builder)
                (.extensions [table-extension strikethrough-extension])
                (.build)))

(defn- children [node]
  (take-while some? (iterate #(.getNext %) (.getFirstChild node))))

(defn- parse-markdown [s] (.parse parser s))

(defmulti render class)

(defn render-children [node]
  (let [children (children node)]
    (cond (nil? (seq children)) nil
          (= 1 (count children)) (render (first children))
          :else (map render children))))

(defmethod render Document [node] (render-children node))
(defmethod render Heading [node] [(keyword (str "h" (.getLevel node))) (render-children node)])
(defmethod render Text [node] (.getLiteral node))
(defmethod render Paragraph [node] [:p (render-children node)])
(defmethod render BulletList [node] [:ul (render-children node)])
(defmethod render OrderedList [node] [:ol (render-children node)])
(defmethod render ListItem [node] [:li (render-children node)])
(defmethod render Code [node] [:code (.getLiteral node)])
(defmethod render BlockQuote [node] [:blockquote (render-children node)])
(defmethod render IndentedCodeBlock [node] [:pre [:code (.getLiteral node)]])
(defmethod render FencedCodeBlock [node] [:pre [:code {:class (.getInfo node)} (.getLiteral node)]])
(defmethod render Link [node] [:a {:href (.getDestination node)} (render-children node)])
(defmethod render Image [node] [:img {:src (.getDestination node) :alt (render-children node) :title (.getTitle node)}])
(defmethod render Emphasis [node] [:em (render-children node)])
(defmethod render StrongEmphasis [node] [:strong (render-children node)])
(defmethod render ThematicBreak [_] [:hr])
(defmethod render SoftLineBreak [_] " ")
(defmethod render HardLineBreak [_] [:br])
(defmethod render HtmlInline [node] (.getLiteral node))
(defmethod render HtmlBlock [node] (.getLiteral node))
(defmethod render TableBlock [node] [:table (render-children node)])
(defmethod render TableHead [node] [:thead (render-children node)])
(defmethod render TableRow [node] [:tr (render-children node)])
(defmethod render TableBody [node] [:tbody (render-children node)])
(defmethod render TableCell [node] [:td (render-children node)])
(defmethod render Strikethrough [node] [:s (render-children node)])

(defn ->hiccup [md] (when md (render (parse-markdown md))))

(def renderer (-> (HtmlRenderer/builder)
                  (.extensions [table-extension strikethrough-extension])
                  (.build)))

(defn ->html [md] (when md (.render renderer (parse-markdown md))))
