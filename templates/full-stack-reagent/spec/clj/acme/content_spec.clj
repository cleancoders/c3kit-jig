(ns acme.content-spec
  (:require [acme.content :as sut]
            [acme.spec-helper]
            [acme.test-data :as test-data]
            [clojure.java.io :as io]
            [speclj.core :refer :all]))

(describe "acme.content discovery"

  (before (sut/load!))

  (it "discovers the blog type from content/blog"
    (should-contain :blog (sut/types)))

  (it "loads the sample post under :blog"
    (let [post (sut/find-post :blog "2026-05-12-hello-world")]
      (should= "Hello, world" (-> post :meta :title))
      (should= "2026-05-12-hello-world" (:permalink post))
      (should (string? (:markdown post)))
      (should (re-find #"Hello, world" (:markdown post)))))

  (it "lists posts under a type"
    (let [posts (sut/posts :blog)]
      (should (seq posts))
      (should-contain "2026-05-12-hello-world" (map :permalink posts))))

  (it "filters unpublished posts"
    (should (every? #(-> % :meta :published?) (sut/posts :blog))))

  (it "find-post returns nil for unknown permalink"
    (should-be-nil (sut/find-post :blog "no-such-thing")))

  (it "types returns nil for unknown type"
    (should-be-nil (sut/posts :nope))))

(describe "reserved-route collision"

  (it "throws on a type whose dir name collides with a reserved route"
    (let [tmp     (java.io.File/createTempFile "content-collision" "")
          _       (.delete tmp)
          root    (io/file tmp)
          api-dir (io/file root "api")
          post    (io/file api-dir "x")]
      (.mkdirs post)
      (spit (io/file post "meta.edn") "{:title \"x\" :published? true}")
      (spit (io/file post "content.md") "x")
      (with-redefs [clojure.java.io/file (fn [& parts]
                                           (if (= ["content"] (vec parts))
                                             root
                                             (apply #'clojure.java.io/file parts)))]
        (should-throw clojure.lang.ExceptionInfo #"reserved route" (sut/load!))))))

(describe "web-post handler"

  (test-data/with-memory-kinds :user)
  (before (sut/load!))

  (it "renders the rich-client layout with :seo/preview"
    (let [handler  (sut/web-post :blog)
          response (handler {:params {:permalink "2026-05-12-hello-world"}})]
      (should= 200 (:status response))
      (should (re-find #"Hello, world" (:body response)))
      (should (re-find #"<h1>Hello, world</h1>" (:body response)))))

  (it "returns the not-found layout for unknown permalink"
    (let [handler  (sut/web-post :blog)
          response (handler {:params {:permalink "no-such-thing"}})]
      (should= 200 (:status response))
      (should (re-find #"Not Found" (:body response))))))

(describe "web-list handler"

  (test-data/with-memory-kinds :user)
  (before (sut/load!))

  (it "renders the rich-client layout with a list preview"
    (let [handler  (sut/web-list :blog)
          response (handler {})]
      (should= 200 (:status response))
      (should (re-find #"Hello, world" (:body response))))))

(describe "post->markdown"

  (it "prepends YAML front-matter"
    (let [post {:permalink "abc"
                :meta      {:title "T" :description "D" :published? true :published-at "2026-05-12" :tags ["a" "b"]}
                :markdown  "# Body"}
          out  (sut/post->markdown post)]
      (should (clojure.string/starts-with? out "---\n"))
      (should (re-find #"title: T" out))
      (should (re-find #"description: D" out))
      (should (re-find #"permalink: abc" out))
      (should (re-find #"published-at: 2026-05-12" out))
      (should (re-find #"tags: \[a, b\]" out))
      (should (re-find #"\n---\n\n# Body" out)))))

(describe "posts->markdown-index"

  (it "renders an H1 + bullet list of links"
    (let [posts [{:permalink "p1" :meta {:title "T1" :description "D1" :published? true :published-at "2026-05-12"}}
                 {:permalink "p2" :meta {:title "T2" :description nil    :published? true :published-at "2026-05-11"}}]
          out   (sut/posts->markdown-index :blog posts)]
      (should (re-find #"# Blog" out))
      (should (re-find #"- \[T1\]\(/blog/p1\) — D1" out))
      (should (re-find #"- \[T2\]\(/blog/p2\)\n" out))
      (should-not (re-find #"D2" out)))))

(describe "Accept: text/markdown on content routes"

  (test-data/with-memory-kinds :user)
  (before (sut/load!))

  (it "web-post returns markdown with front-matter when Accept: text/markdown"
    (let [handler  (sut/web-post :blog)
          response (handler {:headers {"accept" "text/markdown"} :params {:permalink "2026-05-12-hello-world"}})]
      (should= 200 (:status response))
      (should= "text/markdown; charset=utf-8" (get-in response [:headers "Content-Type"]))
      (should (clojure.string/starts-with? (:body response) "---\n"))
      (should (re-find #"title: Hello, world" (:body response)))
      (should (re-find #"# Hello, world" (:body response)))))

  (it "web-post returns 404 not-found-shaped body when post missing and md requested"
    (let [handler  (sut/web-post :blog)
          response (handler {:headers {"accept" "text/markdown"} :params {:permalink "no-such-thing"}})]
      (should (re-find #"Not Found" (:body response)))))

  (it "web-list returns markdown index when Accept: text/markdown"
    (let [handler  (sut/web-list :blog)
          response (handler {:headers {"accept" "text/markdown"}})]
      (should= 200 (:status response))
      (should= "text/markdown; charset=utf-8" (get-in response [:headers "Content-Type"]))
      (should (re-find #"# Blog" (:body response)))
      (should (re-find #"- \[Hello, world\]\(/blog/2026-05-12-hello-world\)" (:body response))))))
