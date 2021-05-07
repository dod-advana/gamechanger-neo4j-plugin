package policy.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import org.neo4j.graphdb.*;
import org.neo4j.internal.helpers.collection.Iterators;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import policy.utils.Util;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static java.util.Objects.isNull;
import static policy.utils.JsonUtils.getStringListFromJsonArray;
import static policy.utils.JsonUtils.loadJson;
import static policy.utils.Util.setProperty;

public class CreateUnknownDocsAndReferences {

    // This gives us a log instance that outputs messages to the
    // standard log, normally found under `data/log/console.log`
    @Context
    public Log log;

    @Context
    public GraphDatabaseService db;

    /**
     * Creates UKN_Document nodes for any references that is not in the db
     */
    @Procedure(value = "policy.createUKNDocumentNodesAndAllReferences", mode = Mode.WRITE)
    @Description("Creates UKN_Document nodes for any references that is not in the db.")
    public Stream<Util.Outgoing> createUKNDocumentNodesAndAllReferences() {
        try (Transaction tx = db.beginTx())
        {
            Util.Outgoing out = handleCreateUKNDocumentNodesAndAllReferences(tx, log);
            tx.commit();
            return Stream.of(out);
        } catch (Exception e) {
            throw new RuntimeException("Error creating UKN_Document Nodes and References", e);
        }
    }

    public Util.Outgoing handleCreateUKNDocumentNodesAndAllReferences(Transaction tx, Log log) {
        try {
            int nodesCreated = 0;
            int propertiesSet = 0;
            int relationshipsCreated = 0;

            Map<String, List<Node>> refMap = new HashMap<>();
            Map<Node, List<String>> mapDocsToReferences = new HashMap<>();
            List<Node> documentNodes = tx.findNodes(Label.label("Document")).stream().collect(Collectors.toList());

            // Loop through the nodes creating a ref map
            for (Node docNode : documentNodes ) {
                // Add this doc to refList
                if (refMap.containsKey(docNode.getProperty("ref_name").toString())) {
                    refMap.get(docNode.getProperty("ref_name").toString()).add(docNode);
                } else {
                    List<Node> tmp = new ArrayList<>();
                    tmp.add(docNode);
                    refMap.put(docNode.getProperty("ref_name").toString(), tmp);
                }

                List<String> refs = new ArrayList<>();

                // Now create an entry for each ref in the ref list
                for (String ref : (String[]) docNode.getProperty("ref_list")) {
                    if (!refMap.containsKey(ref)) {
                        refMap.put(ref, new ArrayList<>());
                        refs.add(ref);
                    }
                }

                mapDocsToReferences.put(docNode, refs);
            }

            // Based on the map, empty lists mean no documents for that reference so create a UNKN_Document Node
            // also create relationships
            Map<String, Integer> uknDocsAndRefsOutput = createUnknownDocsAndReferences(refMap, mapDocsToReferences, tx, log);
            nodesCreated += uknDocsAndRefsOutput.get("nodesCreated");
            propertiesSet += uknDocsAndRefsOutput.get("propertiesSet");
            relationshipsCreated += uknDocsAndRefsOutput.get("relationshipsCreated");

            return new Util.Outgoing(nodesCreated, relationshipsCreated, propertiesSet);
        } catch (Exception e) {
            throw new RuntimeException("Can't parse json", e);
        }
    }

    private Map<String, Integer> createUnknownDocsAndReferences(Map<String, List<Node>> refMap, Map<Node, List<String>> mapDocsToReferences, Transaction tx, Log log) {
        int nodesCreated = 0;
        int propertiesSet = 0;
        int relationshipsCreated = 0;

        // Loop through doc nodes and create references to the nodes in the refMap, if no nodes exist then create a UKN_Document
        for(Node docNode : mapDocsToReferences.keySet()) {
            int tmpNodesCreated = 0;
            int tmpPropertiesSet = 0;
            int tmpRelationshipsCreated = 0;
            for (String ref : (String[]) docNode.getProperty("ref_list")) {
                List<Node> refNodes = refMap.get(ref);
                if (refNodes.isEmpty()) {
                    Node newUKNNode = tx.createNode(Label.label("UKN_Document"));
                    tmpNodesCreated++;
                    newUKNNode.setProperty("doc_id", "UKN Document: " + ref);
                    newUKNNode.setProperty("ref_name", ref);
                    tmpPropertiesSet += 2;
                    docNode.createRelationshipTo(newUKNNode, RelationshipType.withName("REFERENCES_UKN"));
                    tmpRelationshipsCreated++;
                } else {
                    for (Node refDoc : refNodes) {
                        docNode.createRelationshipTo(refDoc, RelationshipType.withName("REFERENCES"));
                        tmpRelationshipsCreated++;
                    }
                }
            }
            log.info(String.format("%d nodes created, %d relationships created, %d properties set for: %s", tmpNodesCreated, tmpRelationshipsCreated, tmpPropertiesSet, docNode.getProperty("doc_id")));
            nodesCreated += tmpNodesCreated;
            propertiesSet += tmpPropertiesSet;
            relationshipsCreated += tmpRelationshipsCreated;
        }

        return Map.ofEntries(
                entry("nodesCreated", nodesCreated),
                entry("propertiesSet", propertiesSet),
                entry("relationshipsCreated", relationshipsCreated)
        );
    }

    private int setProperties(Node node, Map<String, Object> properties)  {
        if (node == null) return 0;
        int propsSet = 0;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            setProperty(node, entry.getKey(), entry.getValue());
            propsSet++;
        }
        return propsSet;
    }
}
