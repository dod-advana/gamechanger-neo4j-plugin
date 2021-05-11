package policy.ingest;

import org.junit.jupiter.api.*;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CreateUnknownDocsAndReferencesTest {

    private static final Config driverConfig = Config.builder().withoutEncryption().build();
    private Neo4j embeddedDatabaseServer;

    @BeforeAll
    void initializeNeo4j() throws IOException {

        var sw = new StringWriter();
        try (var in = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/documents.cypher")))) {
            in.transferTo(sw);
            sw.flush();
        }

        this.embeddedDatabaseServer = Neo4jBuilders.newInProcessBuilder()
                .withProcedure(CreateUnknownDocsAndReferences.class)
                .withFixture(sw.toString())
                .build();
    }

    @Test
    public void shouldCreateUnknownDocumentsAndReferences() {
        try(Driver driver = GraphDatabase.driver(this.embeddedDatabaseServer.boltURI(), driverConfig);
            Session session = driver.session()) {

            // When
            Record record = session.run( "CALL policy.createUKNDocumentNodesAndAllReferences()").single();

            assertEquals("The outgoing should match the expected nodes created", 2, record.get("nodesCreated").asInt());
            assertEquals("The outgoing should match the expected properties set", 20, record.get("propertiesSet").asInt());
            assertEquals("The outgoing should match the expected relationships created", 4, record.get("relationshipsCreated").asInt());
        }
    }
}
