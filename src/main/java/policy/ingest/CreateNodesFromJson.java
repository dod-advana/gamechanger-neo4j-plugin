package policy.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import org.neo4j.graphdb.*;
import policy.utils.Util;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static java.util.Objects.isNull;
import static policy.utils.JsonUtils.getStringListFromJsonArray;
import static policy.utils.JsonUtils.loadJson;
import static policy.utils.Util.setProperty;

public class CreateNodesFromJson {

    // This gives us a log instance that outputs messages to the
    // standard log, normally found under `data/log/console.log`
    @Context
    public Log log;

    @Context
    public GraphDatabaseService db;

    /**
     * This procedure takes a document json as a string and creates the nodes associated with the json
     * @param json The json string for the document to be ingested
     */
    @Procedure(value = "policy.createDocumentNodesFromJson", mode = Mode.WRITE)
    @Description("Takes in a document json and creates the nodes and relationships based on the content.")
    public Stream<Util.Outgoing> createDocumentNodesFromJson(@Name("json") String json) {
        try (Transaction tx = db.beginTx())
        {
            Util.Outgoing out = handleCreateDocumentNodesFromJson(json, tx, log);
            tx.commit();
            return Stream.of(out);
        } catch (Exception e) {
            throw new RuntimeException("Error creating node from json", e);
        }
    }

    /**
     * This procedure takes in a json string with all the entities to populate neo4j in one call
     * @param json The json string of the entities to be ingested
     * @return Stream
     */
    @Procedure(value = "policy.createEntityNodesFromJson", mode = Mode.WRITE)
    @Description("Takes in a document json and creates the nodes and relationships based on the content.")
    public Stream<Util.Outgoing> createEntityNodesFromJson(@Name("json") String json) {
        try (Transaction tx = db.beginTx())
        {
            Util.Outgoing out = handleCreateEntityNodesFromJson(json, tx, log);
            tx.commit();
            return Stream.of(out);
        } catch (Exception e) {
            throw new RuntimeException("Error creating node from json", e);
        }
    }

    public Util.Outgoing handleCreateDocumentNodesFromJson(String json, Transaction tx, Log log) {
        try {
            int nodesCreated = 0;
            int propertiesSet = 0;
            int relationshipsCreated = 0;

            JsonNode jsonNode = loadJson(json, true);

            String docId = jsonNode.get("id").asText("");

            Node node = tx.findNode(Label.label("Document"), "doc_id", docId);
            if (isNull(node)) {
                node = tx.createNode(Util.labels(Collections.singletonList("Document")));
                nodesCreated++;
            }

            // Keyw_5 Array
            List<String> keyw_5 = getStringListFromJsonArray(jsonNode.get("keyw_5"));

            // Topics Object
            JsonNode topicsNode = jsonNode.get("topics_rs");
            List<String> topicStrings = new ArrayList<>();
            Map<String, Float> topics = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> topicFields = topicsNode.fields();
            while (topicFields.hasNext()) {
                Map.Entry<String, JsonNode> jsonField = topicFields.next();
                topicStrings.add(jsonField.getKey());
                topics.put(jsonField.getKey(), jsonField.getValue().floatValue());
            }
            Map<String, Integer> topicsOutput = createTopicNodesAndRelationships(node, topics, tx);
            nodesCreated += topicsOutput.get("nodesCreated");
            propertiesSet += topicsOutput.get("propertiesSet");
            relationshipsCreated += topicsOutput.get("relationshipsCreated");

            // Entities Object
            JsonNode entitiesNode = jsonNode.get("entities");
            Map<String, Integer> entitiesOutput = createEntityNodesAndRelationships(node, entitiesNode, tx);
            nodesCreated += entitiesOutput.get("nodesCreated");
            propertiesSet += entitiesOutput.get("propertiesSet");
            relationshipsCreated += entitiesOutput.get("relationshipsCreated");

            // Reference info
            String docNum = jsonNode.get("doc_num").asText("");
            String docType = jsonNode.get("doc_type").asText("");
            String refName;
            if (!docNum.equals("") && !docType.equals("")) {
                refName = docType + " " + docNum;
            } else {
                refName = docId.split(",")[0].replace(".pdf_0", "");
            }

            // References Array
            List<String> references = getStringListFromJsonArray(jsonNode.get("ref_list"));

            Map<String, Object> properties = Map.ofEntries(
                entry("doc_id", docId),
                entry("keyw_5", keyw_5),
                entry("topics", topicStrings),
                entry("ref_list", references),
                entry("filename", jsonNode.get("filename").asText("")),
                entry("title", jsonNode.get("title").asText().replace("\"", "'")),
                entry("display_title_s", jsonNode.get("display_title_s").asText("").replace("\"", "'")),
                entry("display_org_s", jsonNode.get("display_org_s").asText("")),
                entry("display_doc_type_s", jsonNode.get("display_doc_type_s").asText("")),
                entry("access_timestamp_dt", jsonNode.get("access_timestamp_dt").asText("")),
                entry("publication_date_dt", jsonNode.get("publication_date_dt").asText("")),
                entry("crawler_used_s", jsonNode.get("crawler_used_s").asText("")),
                entry("source_fqdn_s", jsonNode.get("source_fqdn_s").asText("")),
                entry("source_page_url_s", jsonNode.get("source_page_url_s").asText("")),
                entry("download_url_s", jsonNode.get("download_url_s").asText("")),
                entry("cac_login_required_b", jsonNode.get("cac_login_required_b").asBoolean(false)),
                entry("doc_num", docNum),
                entry("doc_type", docType),
                entry("summary_30", jsonNode.get("summary_30").asText().replace("\"", "'").replace("\\", "/")),
                entry("type", jsonNode.get("type").asText("")),
                entry("name", jsonNode.get("filename").asText("").split(".pdf")[0]),
                entry("ref_name", refName),
                entry("page_count", jsonNode.get("page_count").asInt(0)),
                entry("init_date", jsonNode.get("init_date").asText("")),
                entry("change_date", jsonNode.get("change_date").asText("")),
                entry("author", jsonNode.get("author").asText("")),
                entry("signature", jsonNode.get("signature").asText("")),
                entry("subject", jsonNode.get("subject").asText("")),
                entry("classification", jsonNode.get("classification").asText("")),
                entry("group_s", jsonNode.get("group_s").asText("")),
                entry("pagerank_r", jsonNode.get("pagerank_r").asDouble(0)),
                entry("kw_doc_score_r", jsonNode.get("kw_doc_score_r").asDouble(0)),
                entry("version_hash_s", jsonNode.get("version_hash_s").asText("")),
                entry("is_revoked_b", jsonNode.get("is_revoked_b").asBoolean(false))
            );

            propertiesSet += setProperties(node, properties);

            log.info(String.format("%d nodes created, %d relationships created, %d properties set for: %s", nodesCreated, relationshipsCreated, propertiesSet, docId));
            return new Util.Outgoing(nodesCreated, relationshipsCreated, propertiesSet);
        } catch (Exception e) {
            throw new RuntimeException("Can't parse json", e);
        }
    }

    public Util.Outgoing handleCreateEntityNodesFromJson(String json, Transaction tx, Log log) {
        try {
            int nodesCreated = 0;
            int propertiesSet = 0;
            int relationshipsCreated = 0;

            JsonNode jsonNode = loadJson(json, true);

            for (final JsonNode entityNode : jsonNode) {
                String agencyName = entityNode.get("Agency_Name").asText("");
                String parentName = entityNode.get("Parent_Agency").asText("");

                Node node = tx.findNode(Label.label("Entity"), "name", agencyName);
                if (isNull(node)) {
                    node = tx.createNode(Util.labels(Collections.singletonList("Entity")));
                    nodesCreated++;
                }

                Map<String, Object> properties = Map.ofEntries(
                    entry("name", entityNode.get("Agency_Name").asText("")),
                    entry("aliases", entityNode.get("Agency_Aliases").asText("")),
                    entry("website", entityNode.get("Website").asText("")),
                    entry("image", entityNode.get("Agency_Image").asText("")),
                    entry("address", entityNode.get("Address").asText("")),
                    entry("phone", entityNode.get("Phone").asText("")),
                    entry("tty", entityNode.get("TTY").asText("")),
                    entry("tollfree", entityNode.get("TollFree").asText("")),
                    entry("branch", entityNode.get("Government_Branch").asText("")),
                    entry("type", "organization")
                );
                propertiesSet += setProperties(node, properties);

                Node parentNode = tx.findNode(Label.label("Entity"), "name", parentName);
                if (isNull(parentNode)) {
                    parentNode = tx.createNode(Util.labels(Collections.singletonList("Entity")));
                    parentNode.setProperty("name", parentName);
                    nodesCreated++;
                    propertiesSet++;
                }
                node.createRelationshipTo(parentNode, RelationshipType.withName("CHILD_OF"));
                relationshipsCreated++;

                String[] relatedEntities = entityNode.get("Related_Agency").asText("").split(";");
                for (final String relatedEntity : relatedEntities) {
                    Node relatedNode = tx.findNode(Label.label("Entity"), "name", relatedEntity.trim());
                    if (isNull(relatedNode)) {
                        relatedNode = tx.createNode(Util.labels(Collections.singletonList("Entity")));
                        relatedNode.setProperty("name", relatedEntity.trim());
                        nodesCreated++;
                        propertiesSet++;
                    }
                    node.createRelationshipTo(relatedNode, RelationshipType.withName("RELATED_TO"));
                    relatedNode.createRelationshipTo(node, RelationshipType.withName("RELATED_TO"));
                    relationshipsCreated += 2;
                }
            }

            log.info(String.format("%d nodes created, %d relationships created, %d properties set for entities list", nodesCreated, relationshipsCreated, propertiesSet));
            return new Util.Outgoing(nodesCreated, relationshipsCreated, propertiesSet);
        } catch (Exception e) {
            throw new RuntimeException("Can't parse json", e);
        }
    }

    private Map<String, Integer> createTopicNodesAndRelationships(Node documentNode, Map<String, Float> topicsMap, Transaction tx) {
        Integer nodesCreated = 0;
        Integer propertiesSet = 0;
        Integer relationshipsCreated = 0;

        for (String key : topicsMap.keySet()) {
            Node tmp = tx.findNode(Label.label("Topic"), "name", key);
            if (isNull(tmp)) {
                tmp = tx.createNode(Util.labels(Collections.singletonList("Topic")));
                tmp.setProperty("name", key);
                nodesCreated++;
                propertiesSet++;
            }
            Iterable<Relationship> relationships = documentNode.getRelationships(Direction.OUTGOING, RelationshipType.withName("CONTAINS"));
            boolean hasRelationship = false;
            for (Relationship rel : relationships) {
                if (rel.getEndNode().getProperty("name") == key) {
                    hasRelationship = true;
                }
            }
            if (!hasRelationship) {
                Relationship containsRel = documentNode.createRelationshipTo(tmp, RelationshipType.withName("CONTAINS"));
                containsRel.setProperty("relevancy", topicsMap.get(key));
                relationshipsCreated++;
                propertiesSet++;
                Relationship isInRel = tmp.createRelationshipTo(documentNode, RelationshipType.withName("IS_IN"));
                isInRel.setProperty("relevancy", topicsMap.get(key));
                relationshipsCreated++;
                propertiesSet++;
            }
        }
        return Map.ofEntries(
            entry("nodesCreated", nodesCreated),
            entry("propertiesSet", propertiesSet),
            entry("relationshipsCreated", relationshipsCreated)
        );
    }

    private Map<String, Integer> createEntityNodesAndRelationships(Node documentNode, JsonNode entitiesNode, Transaction tx) {
        Integer nodesCreated = 0;
        Integer propertiesSet = 0;
        Integer relationshipsCreated = 0;

        JsonNode entityPars = entitiesNode.get("entityPars");
        JsonNode entityCounts = entitiesNode.get("entityCounts");
        Iterator<Map.Entry<String, JsonNode>> entityFields = entityPars.fields();
        while (entityFields.hasNext()) {
            Map.Entry<String, JsonNode> jsonField = entityFields.next();
            String key = jsonField.getKey();
            Integer mentionsCount = entityCounts.get(key).asInt(0);

            Node tmp = tx.findNode(Label.label("Entity"), "name", key);
            if (isNull(tmp)) {
                tmp = tx.createNode(Util.labels(Collections.singletonList("Entity")));
                tmp.setProperty("name", key);
                nodesCreated++;
                propertiesSet++;
            }
            Iterable<Relationship> relationships = documentNode.getRelationships(Direction.OUTGOING, RelationshipType.withName("MENTIONS"));
            boolean hasRelationship = false;
            for (Relationship rel : relationships) {
                if (rel.getEndNode().getProperty("name") == key) {
                    hasRelationship = true;
                }
            }
            if (!hasRelationship) {
                Relationship containsRel = documentNode.createRelationshipTo(tmp, RelationshipType.withName("MENTIONS"));
                containsRel.setProperty("count", mentionsCount);
                relationshipsCreated++;
                propertiesSet++;
            }
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
