(ns bookfriend.test.linkshare
  (:use [bookfriend.linkshare])
  (:use [clojure.test]))

(def result-xml "<result>
    <TotalMatches>29</TotalMatches>
    <TotalPages>1</TotalPages>
    <PageNumber>1</PageNumber>
    <item>
        <mid>36889</mid>
        <merchantname>Barnes&amp;Noble.com</merchantname>
        <linkid>9780203451588</linkid>
        <createdon>2011-08-04/18:08:12</createdon>
        <sku>9780203451588</sku>
        <productname>Political Corruption</productname>
        <category>
            <primary>eBooks</primary>
            <secondary>True Crime</secondary>
        </category>
        <price currency=\"USD\">52.95</price>
        <upccode>123abc</upccode>
        <description>
            <short>Robert Harris,NOOKbook (eBook)</short>
            <long/>
        </description>
        <keywords/>
        <linkurl>http://click.linksynergy.com/fs-bin/click?id=2Pjrw9gq7Wo&amp;offerid=229293.9780203451588&amp;type=15&amp;subid=0</linkurl>
        <imageurl>http://images.barnesandnoble.com/pimages/gresources/ImageNA_product.gif</imageurl>
    </item></result>")

(def expected-map {
      :mid "36889"
      :merchantname "Barnes&Noble.com"
      :createdon "2011-08-04/18:08:12"
      :linkid "9780203451588"
      :sku "9780203451588"
      :productname "Political Corruption"
      :category { :primary "eBooks"  :secondary "True Crime" }
      :price "52.95"
      :upccode "123abc"
      :description { :short "Robert Harris,NOOKbook (eBook)"
                     :long nil }
      :keywords nil
      :linkurl "http://click.linksynergy.com/fs-bin/click?id=2Pjrw9gq7Wo&offerid=229293.9780203451588&type=15&subid=0"
      :imageurl "http://images.barnesandnoble.com/pimages/gresources/ImageNA_product.gif"
  })

(deftest test-productsearch-xml-parsing
  (let [res (parse-product-search-result result-xml)]
    (is (= "29" (:TotalMatches res)))
    (is (= "1" (:TotalPages res)))
    (is (= expected-map (first (:items res))))))

