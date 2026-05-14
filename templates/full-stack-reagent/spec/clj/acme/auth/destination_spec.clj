(ns acme.auth.destination-spec
  (:require [acme.auth.destination :as sut]
            [c3kit.wire.spec-helper :as wire]
            [speclj.core :refer :all]))


(declare request)

(describe "Destination"

  (before (sut/configure! (sut/->DefaultDestinationAdapter)))

  (context "preserve"

    (it "get"
      (let [response (sut/preserve {} {:request-method :get :uri "/somewhere"})]
        (should= {:method :get :uri "/somewhere" :query-string nil} (-> response :session :destination))))

    (it "existing session is preserved"
      (let [response (sut/preserve {} {:request-method :get :uri "/somewhere" :session {:foo :bar}})]
        (should= :bar (-> response :session :foo))))

    (it "ajax get"
      (let [request  {:request-method :get :uri "/ajax/somewhere" :headers {"referer" "http://foo.com/origin"}}
            response (sut/preserve {} request)]
        (should= "/origin" (-> response :session :destination :uri))))

    (it "ajax get hacked"
      (let [request  {:request-method :get :uri "/ajax/somewhere" :headers {"referer" "select * from user"}}
            response (sut/preserve {} request)]
        (should= "/" (-> response :session :destination :uri))))

    (it "post"
      (let [request  {:request-method :post :uri "/somewhere" :params {:foo "bar"}}
            response (sut/preserve {} request)]
        (should= {:method :post :uri "/somewhere" :params {:foo "bar"}} (-> response :session :destination))))

    )

  (context "add-to-payload"

    (with request {:session {:destination {:method :get :uri "/easy-town"}}})

    (it "get stored in the response"
      (let [response (sut/add-to-payload @request :some-user)]
        (should= "/easy-town" (-> response :body :payload :destination))
        (should-not-contain :destination (:session response))))

    (it "post stored in the response"
      (let [destination {:method :post :params {:foo "bar" :fizz "bang"}}
            response    (assoc-in @request [:session :destination] destination)
            response    (sut/add-to-payload response :some-user)]
        (should= "/redirect" (-> response :body :payload :destination))
        (should= destination (sut/from-session response))))

    (it "default destination"
      (should= "/" (sut/default-user-destination nil)))

    (it "simple get"
      (should= "/easy-town" (sut/build-uri (sut/calculate-destination @request :some-user))))

    (it "get with query-string"
      (let [request (assoc-in @request [:session :destination :query-string] "foo=bar&fizz=bang")]
        (should= "/easy-town?foo=bar&fizz=bang" (sut/build-uri (sut/calculate-destination request :some-user)))))

    (it "post needs special handling"
      (let [response (-> (assoc-in @request [:session :destination :method] :post)
                         (sut/add-to-payload :some-user))]
        (should= "/redirect" (-> response :body :payload :destination))
        (should-contain :destination (:session response))))

    (it "no destination, no user"
      (should= "/" (sut/build-uri (sut/calculate-destination {} nil))))

    (it "no destination, normal user"
      (should= "/" (sut/build-uri (sut/calculate-destination {} :some-user))))

    )

  (context "web-redirect"

    (it "with no destination"
      (let [response (sut/web-redirect {})]
        (wire/should-redirect-to response "/")))

    (it "with :get destination"
      (let [response (sut/web-redirect {:session {:destination {:method :get :uri "/somewhere"}}})]
        (wire/should-redirect-to response "/somewhere")))

    (it "with post method"
      (let [response (sut/web-redirect {:session {:destination {:method :post
                                                                :uri    "/post-form"
                                                                :params {:foo "bar" :fizz "bang"}}}})]
        (should-be-nil (sut/from-session response))
        (should-contain "You are being redirected" (:body response))
        (should-contain "<form" (:body response))
        (should-contain "method=\"post\"" (:body response))
        (should-contain "<input" (:body response))
        (should-contain "name=\"foo\"" (:body response))
        (should-contain "value=\"bar\"" (:body response))))
    )

  )
