 package policy.ingest;

 import org.neo4j.graphdb.Label;
 import org.neo4j.logging.NullLog;

 import org.junit.Test;
 import org.neo4j.graphdb.Transaction;
 import org.neo4j.harness.junit.rule.Neo4jRule;
 import static org.neo4j.internal.helpers.collection.Iterators.count;

 import org.junit.Rule;
 import policy.utils.Util;

 import static org.junit.Assert.assertEquals;

 public class CreateNodesFromJsonTest {

     static String testDocumentJson = "{\"id\": \"AGO 1976-02.pdf_0\", \"doc_num\": \"1976-02\", \"doc_type\": \"AGO\", \"display_title_s\": \"AGO 1976-02 HQDA GENERAL ORDERS (MULTITLE TITLES BY PARAGRAPHS)\", \"display_org_s\": \"US Army\", \"display_doc_type_s\": \"Document\", \"ref_list\": [\"Executive Order 10600\", \"Executive Order 11046\", \"AR 672-5-1\", \"AR 672-51\", \"AR 672-3\", \"AR 672-31\", \"AR 672-3-1\"], \"access_timestamp_dt\": \"2021-04-08T15:55:09\", \"publication_date_dt\": \"1976-02-03T00:00:00\", \"crawler_used_s\": \"army_pubs\", \"source_fqdn_s\": \"armypubs.army.mil\", \"source_page_url_s\": \"https://armypubs.army.mil/ProductMaps/PubForm/Details.aspx?PUB_ID=42986\", \"download_url_s\": \"https://armypubs.army.mil/epubs/DR_pubs/DR_a/pdf/web/go7602.pdf\", \"cac_login_required_b\": false, \"title\": \"HQDA GENERAL ORDERS (MULTITLE TITLES BY PARAGRAPHS)\", \"keyw_5\": [\"ar 672-5\", \"weyand general\", \"warrant officer\", \"united states\", \"september sergeant\", \"outstanding service\", \"military operations\", \"headquarters company\", \"ar 6725-1\", \"air foree\"], \"filename\": \"AGO 1976-02.pdf\", \"summary_30\": \"fantry, United States Army, for action on 8 February 1968, while a member United States Army, for action on 18 June 1969, while a member of 3d Squad-\", \"type\": \"document\", \"page_count\": 6, \"topics_rs\": {\"award\": 0.25557929273822744, \"samae\": 0.25054542619282344, \"provisions\": 0.1844819893206493, \"heroism\": 0.17931940799917018, \"detaciiment\": 0.16559304007920778}, \"init_date\": \"NA\", \"change_date\": \"NA\", \"author\": \"NA\", \"signature\": \"NA\", \"subject\": \"NA\", \"classification\": \"NA\", \"group_s\": \"AGO 1976-02.pdf_0\", \"pagerank_r\": 3.509842455928412e-05, \"kw_doc_score_r\": null, \"version_hash_s\": \"bb39c1a3cd42a771ee1123efb46052a55f939b9b66be2a452fcc5fea39d51b29\", \"is_revoked_b\": false, \"entities\": {\"entityPars\": {\"United States Army\": [1, 2, 3, 4, 5, 6]}, \"entityCounts\": {\"United States Army\": 6}}}";

     static String testEntitiesJson = "[\n" +
             "  {\n" +
             "    \"Agency_Aliases\": \"NAL\",\n" +
             "    \"Agency_Name\": \"National Agricultural Library\",\n" +
             "    \"Website\": \"https:\\/\\/www.nal.usda.gov\\/main\\/\",\n" +
             "    \"Address\": \"10301 Baltimore Ave. \\nBeltsville, MD 20705\",\n" +
             "    \"Email\": \"\",\n" +
             "    \"Phone\": \"1-301-504-5755\",\n" +
             "    \"TTY\": \"\",\n" +
             "    \"TollFree\": \"\",\n" +
             "    \"Government_Branch\": \"Executive Department Sub-Office\\/Agency\\/Bureau\",\n" +
             "    \"Parent_Agency\": \"United States Department of Agriculture\",\n" +
             "    \"Related_Agency\": \"\",\n" +
             "    \"Agency_Image\": \"https:\\/\\/upload.wikimedia.org\\/wikipedia\\/commons\\/thumb\\/0\\/00\\/Seal_of_the_United_States_Department_of_Agriculture.svg\\/1000px-Seal_of_the_United_States_Department_of_Agriculture.svg.png\"\n" +
             "  }\n" +
             "]";

     @Rule
     public Neo4jRule neo4j = new Neo4jRule();

     @Test
     public void shouldCreateADocumentNodeFromJson() {
         CreateNodesFromJson testClass = new CreateNodesFromJson();
         try (Transaction tx = neo4j.defaultDatabaseService().beginTx()) {

             NullLog log = NullLog.getInstance();

             Util.Outgoing expected = new Util.Outgoing(8, 12, 52);
             Util.Outgoing actual = testClass.handleCreateDocumentNodesFromJson(testDocumentJson, tx, log);
             Util.Outgoing duplicate = testClass.handleCreateDocumentNodesFromJson(testDocumentJson, tx, log);

             assertEquals("The outgoing should be all 0s because its a duplicate", 0, duplicate.nodesCreated);
             assertEquals("The outgoing should match the expected nodes created", expected.nodesCreated, actual.nodesCreated);
             assertEquals("The outgoing should match the expected properties set", expected.propertiesSet, actual.propertiesSet);
             assertEquals("The outgoing should match the expected relationships created", expected.relationshipsCreated, actual.relationshipsCreated);
             assertEquals("Should find 1 document node", 1, count(tx.findNodes(Label.label( "Document" ))));
             assertEquals("Should find 5 topic nodes", 5, count(tx.findNodes(Label.label( "Topic" ))));

             tx.commit();
         }
     }

     @Test
     public void shouldCreateAEntityNodeFromJson() {
         CreateNodesFromJson testClass = new CreateNodesFromJson();
         try (Transaction tx = neo4j.defaultDatabaseService().beginTx()) {

             NullLog log = NullLog.getInstance();

             Util.Outgoing expected = new Util.Outgoing(3, 3, 12);
             Util.Outgoing actual = testClass.handleCreateEntityNodesFromJson(testEntitiesJson, tx, log);
             Util.Outgoing duplicate = testClass.handleCreateEntityNodesFromJson(testEntitiesJson, tx, log);

             assertEquals("The outgoing should be all 0s because its a duplicate", 0, duplicate.nodesCreated);
             assertEquals("The outgoing should match the expected nodes created", expected.nodesCreated, actual.nodesCreated);
             assertEquals("The outgoing should match the expected properties set", expected.propertiesSet, actual.propertiesSet);
             assertEquals("The outgoing should match the expected relationships created", expected.relationshipsCreated, actual.relationshipsCreated);
             assertEquals("Should find 3 entity nodes", 3, count(tx.findNodes(Label.label( "Entity" ))));

             tx.commit();
         }
     }
 }