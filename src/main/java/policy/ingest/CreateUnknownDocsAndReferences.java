package policy.ingest;

import org.neo4j.graphdb.*;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import policy.utils.Util;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static java.util.Objects.isNull;

public class CreateUnknownDocsAndReferences {

    private final static String nodesCreatedString = "nodesCreated";
    private final static String propertiesSetString = "propertiesSet";
    private final static String relationshipsCreatedString = "relationshipsCreated";

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
            Map<String, Integer> uknDocsAndRefsOutput = createReferences(refMap, mapDocsToReferences, tx, log);
            nodesCreated += uknDocsAndRefsOutput.get(nodesCreatedString);
            propertiesSet += uknDocsAndRefsOutput.get(propertiesSetString);
            relationshipsCreated += uknDocsAndRefsOutput.get(relationshipsCreatedString);

            return new Util.Outgoing(nodesCreated, relationshipsCreated, propertiesSet);
        } catch (Exception e) {
            throw new RuntimeException("Can't parse json", e);
        }
    }

    private Map<String, Integer> createReferences(Map<String, List<Node>> refMap, Map<Node, List<String>> mapDocsToReferences, Transaction tx, Log log) {
        int nodesCreated = 0;
        int propertiesSet = 0;
        int relationshipsCreated = 0;

        try {
            // Loop through doc nodes and create references to the nodes in the refMap, if no nodes exist then create a UKN_Document
            for (Node docNode : mapDocsToReferences.keySet()) {
                int tmpNodesCreated = 0;
                int tmpPropertiesSet = 0;
                int tmpRelationshipsCreated = 0;
                for (String ref : (String[]) docNode.getProperty("ref_list")) {
                    List<Node> refNodes = refMap.get(ref);
                    Map<String, Integer> tmpCounts;
                    if (refNodes.isEmpty()) {
                        tmpCounts = createUKNDocsAndReferences(ref, docNode, tx);
                    } else {
                        tmpCounts = createKnownDocReferences(refNodes, docNode);
                    }
                    tmpNodesCreated += tmpCounts.get(nodesCreatedString);
                    tmpPropertiesSet += tmpCounts.get(propertiesSetString);
                    tmpRelationshipsCreated += tmpCounts.get(relationshipsCreatedString);
                }
                nodesCreated += tmpNodesCreated;
                propertiesSet += tmpPropertiesSet;
                relationshipsCreated += tmpRelationshipsCreated;
            }
        } catch (Exception e) {
            log.error(e.toString());
        }
        return Map.ofEntries(
                entry(nodesCreatedString, nodesCreated),
                entry(propertiesSetString, propertiesSet),
                entry(relationshipsCreatedString, relationshipsCreated)
        );
    }

    private Map<String, Integer> createUKNDocsAndReferences(String ref, Node docNode, Transaction tx) {
        int nodesCreated = 0;
        int propertiesSet = 0;
        int relationshipsCreated = 0;

        String uknDocLabel = "UKN Document: ";
        String docIdLabel = "doc_id";

        try {
            Node node = tx.findNode(Label.label("UKN_Document"), docIdLabel, uknDocLabel + ref);
            if (isNull(node)) {
                node = tx.createNode(Util.labels(Collections.singletonList("UKN_Document")));
                node.setProperty(docIdLabel, uknDocLabel + ref);
                propertiesSet++;
                nodesCreated++;
            }
            String docType = ref.split(" ")[0].trim();
            String docNum = ref.replaceFirst(docType, "").trim();
            node.setProperty("ref_name", ref);
            node.setProperty("type", "ukn_document");
            node.setProperty("name", uknDocLabel + ref);
            node.setProperty("title", uknDocLabel + ref);
            node.setProperty("doc_type", docType);
            node.setProperty("doc_num", docNum);
            propertiesSet += 6;
            if (Util.createNonDuplicateRelationship(docNode, node, RelationshipType.withName("REFERENCES_UKN"), log) != null)
                relationshipsCreated++;
        } catch (Exception e) {
            log.error(e.toString());
        }
        return Map.ofEntries(
                entry(nodesCreatedString, nodesCreated),
                entry(propertiesSetString, propertiesSet),
                entry(relationshipsCreatedString, relationshipsCreated)
        );
    }

    private Map<String, Integer> createKnownDocReferences(List<Node> refNodes, Node docNode) {
        int nodesCreated = 0;
        int propertiesSet = 0;
        int relationshipsCreated = 0;

        try {
            for (Node refDoc : refNodes) {
                if (Util.createNonDuplicateRelationship(docNode, refDoc, RelationshipType.withName("REFERENCES"), log) != null)
                    relationshipsCreated++;
            }
        } catch (Exception e) {
            log.error(e.toString());
        }
        return Map.ofEntries(
                entry(nodesCreatedString, nodesCreated),
                entry(propertiesSetString, propertiesSet),
                entry(relationshipsCreatedString, relationshipsCreated)
        );
    }
}
