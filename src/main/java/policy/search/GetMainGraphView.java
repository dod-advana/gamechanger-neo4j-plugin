package policy.search;

import org.neo4j.graphdb.*;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import policy.utils.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GetMainGraphView {
    // This gives us a log instance that outputs messages to the
    // standard log, normally found under `data/log/console.log`
    @Context
    public Log log;

    @Context
    public GraphDatabaseService db;

    /**
     * This procedure takes a document json as a string and creates the nodes associated with the json
     * @param docIds List of docIds to get nodes and relationships for;
     */
    @Procedure(value = "policy.getMainGraphView", mode = Mode.WRITE)
    @Description("Takes in a document json and creates the nodes and relationships based on the content.")
    public Stream<Util.NodeRelationshipWrapper> getMainGraphView(@Name("docIds") List<String> docIds) {
        List<Node> nodes = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();

        try {
            // Get the documents
            ResourceIterator<Node> docs = db.beginTx().findNodes(Label.label("Document"));

            while (docs.hasNext()) {
                Node t_node = docs.next();

                if (docIds.contains(t_node.getProperty("doc_id"))){

                    // Add the node
                    nodes.add(t_node);

                    // Look for references
                    Iterable<Relationship> t_rels = t_node.getRelationships(Direction.OUTGOING, RelationshipType.withName("REFERENCES"));
                    for (Relationship rel : t_rels) {
                        if (docIds.contains(rel.getEndNode().getProperty("doc_id")) && !rel.getEndNode().equals(t_node)) {
                            relationships.add(rel);
                        }
                    }

                    // Look for entities
                    Iterable<Relationship> t_ents = t_node.getRelationships(Direction.OUTGOING, RelationshipType.withName("MENTIONS"));
                    for (Relationship rel : t_ents) {
                        relationships.add(rel);
                        nodes.add(rel.getEndNode());
                    }

                    // Look for topics
                    Iterable<Relationship> t_topics = t_node.getRelationships(Direction.OUTGOING, RelationshipType.withName("CONTAINS"));
                    for (Relationship rel : t_topics) {
                        relationships.add(rel);
                        nodes.add(rel.getEndNode());
                    }

                }

            }

        } catch (Exception e) {
            throw new RuntimeException("Error creating node from json", e);
        }

        return Stream.of(new Util.NodeRelationshipWrapper(nodes, relationships));
    }
}
