package policy.search;

import org.junit.jupiter.api.*;
import org.neo4j.driver.*;
import org.neo4j.graphdb.Transaction;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;
import policy.utils.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.*;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GetMainGraphViewTest {

    private static final Config driverConfig = Config.builder().withoutEncryption().build();
    private static Driver driver;
    private Neo4j embeddedDatabaseServer;

    @BeforeAll
    void initializeNeo4j() throws IOException {

        var sw = new StringWriter();
        try (var in = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/fakeGraph.cypher")))) {
            in.transferTo(sw);
            sw.flush();
        }

        this.embeddedDatabaseServer = Neo4jBuilders.newInProcessBuilder()
                .withProcedure(GetMainGraphView.class)
                .withFixture(sw.toString())
                .build();

        this.driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI(), driverConfig);
    }

    @AfterAll
    void closeDriver(){
        this.driver.close();
        this.embeddedDatabaseServer.close();
    }

    @Test
    public void shouldGetGraphFromSearch() {
        try(Session session = driver.session()) {
            List<String> docIds = asList("AGO 1976-02.pdf_0", "Test 1.pdf_0");

            Record record = session.run("CALL policy.getMainGraphView($docIds) YIELD nodes, relationships RETURN nodes, relationships", Util.map("docIds", docIds)).single();

            assertEquals("The outgoing should match the expected nodes found", 4, record.get("nodes").asList().size());
            assertEquals("The outgoing should match the expected relationships found", 4, record.get("relationships").asList().size());
        }
    }
}
