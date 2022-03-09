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

import java.io.StringWriter;
import java.io.PrintWriter;

public class CreateNodesFromJson {

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
            throw new RuntimeException("Error creating entity node from json", e);
        }
    }

    /**
     * This procedure takes in a json string with all the organizations to populate neo4j in one call
     * @param json The json string of the orgs to be ingested
     * @return Stream
     */
    @Procedure(value = "policy.createOrgNodesFromJson", mode = Mode.WRITE)
    @Description("Takes in the org json and creates the nodes and relationships based on the content.")
    public Stream<Util.Outgoing> createOrgNodesFromJson(@Name("json") String json) {
        try (Transaction tx = db.beginTx())
        {
            Util.Outgoing out = handleCreateOrgNodesFromJson(json, tx, log);
            tx.commit();
            return Stream.of(out);
        } catch (Exception e) {
            throw new RuntimeException("Error creating org node from json", e);
        }
    }

    /**
     * This procedure takes in a json string with all the roles to populate neo4j in one call
     * @param json The json string of the roles to be ingested
     * @return Stream
     */
    @Procedure(value = "policy.createRoleNodesFromJson", mode = Mode.WRITE)
    @Description("Takes in the role json and creates the nodes and relationships based on the content.")
    public Stream<Util.Outgoing> createRoleNodesFromJson(@Name("json") String json) {
        try (Transaction tx = db.beginTx())
        {
            Util.Outgoing out = handleCreateRoleNodesFromJson(json, tx, log);
            tx.commit();
            return Stream.of(out);
        } catch (Exception e) {
            throw new RuntimeException("Error creating role node from json", e);
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
            Map<String, Integer> topicsOutput = createTopicNodesAndRelationships(node, topics, tx, log);
            nodesCreated += topicsOutput.get(nodesCreatedString);
            propertiesSet += topicsOutput.get(propertiesSetString);
            relationshipsCreated += topicsOutput.get(relationshipsCreatedString);

            // Entities Object
            JsonNode entitiesNode = jsonNode.get("entities");
            Map<String, Integer> entitiesOutput = createEntityNodesAndRelationships(node, entitiesNode, tx, log);
            nodesCreated += entitiesOutput.get(nodesCreatedString);
            propertiesSet += entitiesOutput.get(propertiesSetString);
            relationshipsCreated += entitiesOutput.get(relationshipsCreatedString);

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

            return new Util.Outgoing(nodesCreated, relationshipsCreated, propertiesSet);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String sStackTrace = sw.toString();
            log.error(String.format("Error parsing json: %s", sStackTrace));
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
                
                if (Util.createNonDuplicateRelationship(node, parentNode, RelationshipType.withName("CHILD_OF"), log) != null)
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
                    if (Util.createNonDuplicateRelationship(node, relatedNode, RelationshipType.withName("RELATED_TO"), log) != null)
                        relationshipsCreated++;
                    if (Util.createNonDuplicateRelationship(relatedNode, node, RelationshipType.withName("RELATED_TO"), log) != null)
                        relationshipsCreated++;
                }
            }

            return new Util.Outgoing(nodesCreated, relationshipsCreated, propertiesSet);
        } catch (Exception e) {
            throw new RuntimeException("Can't parse json", e);
        }
    }

    private Map<String, Integer> createTopicNodesAndRelationships(Node documentNode, Map<String, Float> topicsMap, Transaction tx, Log log) {
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

            Relationship containsRel = Util.createNonDuplicateRelationship(documentNode, tmp, RelationshipType.withName("CONTAINS"), log);
            if (containsRel != null) {
                containsRel.setProperty("relevancy", topicsMap.get(key));
                relationshipsCreated++;
                propertiesSet++;
            }            
            Relationship isInRel = Util.createNonDuplicateRelationship(tmp, documentNode, RelationshipType.withName("IS_IN"), log);
            if (isInRel != null) {
                isInRel.setProperty("relevancy", topicsMap.get(key));
                relationshipsCreated++;
                propertiesSet++;
            }
        }
        return Map.ofEntries(
            entry(nodesCreatedString, nodesCreated),
            entry(propertiesSetString, propertiesSet),
            entry(relationshipsCreatedString, relationshipsCreated)
        );
    }

    private Map<String, Integer> createEntityNodesAndRelationships(Node documentNode, JsonNode entitiesNode, Transaction tx, Log log) {
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

            Relationship containsRel = Util.createNonDuplicateRelationship(documentNode, tmp, RelationshipType.withName("MENTIONS"), log);
            if (containsRel != null) {
                containsRel.setProperty("count", mentionsCount);
                relationshipsCreated++;
                propertiesSet++;
            }
        }

        return Map.ofEntries(
            entry(nodesCreatedString, nodesCreated),
            entry(propertiesSetString, propertiesSet),
            entry(relationshipsCreatedString, relationshipsCreated)
        );
    }

    public Util.Outgoing handleCreateOrgNodesFromJson(String json, Transaction tx, Log log) {
        try {
            int nodesCreated = 0;
            int propertiesSet = 0;
            int relationshipsCreated = 0;

            JsonNode jsonNode = loadJson(json, true);

            for (final JsonNode orgNode : jsonNode) {
                String orgName = orgNode.get("Name").asText("");
                String orgParentName = orgNode.get("Parent").asText("");
                String orgType = orgNode.get("Type").asText("");
                String orgSubtype = orgNode.get("Subtype").asText("");
                String orgHead = orgNode.get("Head").asText("");

                Node node = tx.findNode(Label.label("Org"), "name", orgName);
                if (isNull(node)) {
                    node = tx.createNode(Util.labels(Collections.singletonList("Org")));
                    nodesCreated++;
                }

                Map<String, Object> properties = Map.ofEntries(
                    entry("name", orgName),
                    // entry("aliases", orgNode.get("Aliases").asText("")),
                    entry("isDODComponent", orgNode.get("DoDComponent").asBoolean(false)),
                    entry("isOSDComponent", orgNode.get("OSDComponent").asBoolean(false)),
                    entry("type", "organization")
                );

                propertiesSet += setProperties(node, properties);

                if (!orgParentName.isEmpty()) {
                    Node parentNode = tx.findNode(Label.label("Org"), "name", orgParentName);
                    if (isNull(parentNode)) {
                        parentNode = tx.createNode(Util.labels(Collections.singletonList("Org")));
                        parentNode.setProperty("name", orgParentName);
                        nodesCreated++;
                        propertiesSet++;
                    }
    
                    if (Util.createNonDuplicateRelationship(node, parentNode, RelationshipType.withName("CHILD_OF"), log) != null)
                        relationshipsCreated++;
                }
                
                // Type
                if (!orgType.isEmpty()) {
                    Node typeNode = tx.findNode(Label.label("Org"), "name", orgType);
                    if (isNull(typeNode)) {
                        typeNode = tx.createNode(Util.labels(Collections.singletonList("Org")));
                        typeNode.setProperty("name", orgType);
                        nodesCreated++;
                        propertiesSet++;
                    }
    
                    if (Util.createNonDuplicateRelationship(node, typeNode, RelationshipType.withName("TYPE_OF"), log) != null)
                        relationshipsCreated++;
                }

                // Subtype
                if (!orgSubtype.isEmpty()) {
                    Node subtypeNode = tx.findNode(Label.label("Org"), "name", orgSubtype);
                    if (isNull(subtypeNode)) {
                        subtypeNode = tx.createNode(Util.labels(Collections.singletonList("Org")));
                        subtypeNode.setProperty("name", orgSubtype);
                        nodesCreated++;
                        propertiesSet++;
                    }
    
                    if (Util.createNonDuplicateRelationship(node, subtypeNode, RelationshipType.withName("TYPE_OF"), log) != null)
                        relationshipsCreated++;
                }

                // Head
                if (!orgHead.isEmpty()) {
                    Node headNode = tx.findNode(Label.label("Role"), "name", orgHead);
                    if (isNull(headNode)) {
                        headNode = tx.createNode(Util.labels(Collections.singletonList("Role")));
                        headNode.setProperty("name", orgHead);
                        nodesCreated++;
                        propertiesSet++;
                    }
    
                    if (Util.createNonDuplicateRelationship(node, headNode, RelationshipType.withName("HAS_HEAD"), log) != null)
                        relationshipsCreated++;
                }
            }
            return new Util.Outgoing(nodesCreated, relationshipsCreated, propertiesSet);
        } catch (Exception e) {
            throw new RuntimeException("Can't parse Orgs json", e);
        }
    }

    public Util.Outgoing handleCreateRoleNodesFromJson(String json, Transaction tx, Log log) {
        try {
            int nodesCreated = 0;
            int propertiesSet = 0;
            int relationshipsCreated = 0;

            JsonNode jsonNode = loadJson(json, true);

            for (final JsonNode roleNode : jsonNode) {
                String roleName = roleNode.get("Name").asText("");
                String roleParentName = roleNode.get("Parent").asText("");
                String roleOrgParentName = roleNode.get("OrgParent").asText("");
                String roleType = roleNode.get("Type").asText("");
                String roleSubtype = roleNode.get("Subtype").asText("");

                Node node = tx.findNode(Label.label("Role"), "name", orgName);
                if (isNull(node)) {
                    node = tx.createNode(Util.labels(Collections.singletonList("Role")));
                    nodesCreated++;
                }

                Map<String, Object> properties = Map.ofEntries(
                    entry("name", roleName),
                    // entry("aliases", roleNode.get("Aliases").asText("")),
                    entry("type", "role")
                );

                propertiesSet += setProperties(node, properties);

                // parent
                if (!roleParentName.isEmpty()) {
                    Node parentNode = tx.findNode(Label.label("Role"), "name", roleParentName);
                    if (isNull(parentNode)) {
                        parentNode = tx.createNode(Util.labels(Collections.singletonList("Role")));
                        parentNode.setProperty("name", roleParentName);
                        nodesCreated++;
                        propertiesSet++;
                    }
    
                    if (Util.createNonDuplicateRelationship(node, parentNode, RelationshipType.withName("CHILD_OF"), log) != null)
                        relationshipsCreated++;
                }
                
                // orgParent
                if (!roleOrgParentName.isEmpty()) {
                    Node orgParentNode = tx.findNode(Label.label("Org"), "name", roleOrgParentName);
                    if (isNull(orgParentNode)) {
                        orgParentNode = tx.createNode(Util.labels(Collections.singletonList("Role")));
                        orgParentNode.setProperty("name", roleOrgParentName);
                        nodesCreated++;
                        propertiesSet++;
                    }
    
                    if (Util.createNonDuplicateRelationship(orgParentNode, node, RelationshipType.withName("HAS_HEAD"), log) != null)
                        relationshipsCreated++;
                }
                

                // Type
                if (!roleType.isEmpty()) {
                    Node typeNode = tx.findNode(Label.label("Role"), "name", roleType);
                    if (isNull(typeNode)) {
                        typeNode = tx.createNode(Util.labels(Collections.singletonList("Role")));
                        typeNode.setProperty("name", roleType);
                        nodesCreated++;
                        propertiesSet++;
                    }
    
                    if (Util.createNonDuplicateRelationship(node, typeNode, RelationshipType.withName("TYPE_OF"), log) != null)
                        relationshipsCreated++;
                }

                // Subtype
                if (!roleSubtype.isEmpty()) {
                    Node subtypeNode = tx.findNode(Label.label("Role"), "name", roleSubtype);
                    if (isNull(subtypeNode)) {
                        subtypeNode = tx.createNode(Util.labels(Collections.singletonList("Role")));
                        subtypeNode.setProperty("name", roleSubtype);
                        nodesCreated++;
                        propertiesSet++;
                    }
    
                    if (Util.createNonDuplicateRelationship(node, subtypeNode, RelationshipType.withName("TYPE_OF"), log) != null)
                        relationshipsCreated++;
                }
            }
            return new Util.Outgoing(nodesCreated, relationshipsCreated, propertiesSet);
        } catch (Exception e) {
            throw new RuntimeException("Can't parse Roles json", e);
        }
    }

    private int setProperties(Node node, Map<String, Object> properties)  {
        if (isNull(node)) return 0;
        int propsSet = 0;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            setProperty(node, entry.getKey(), entry.getValue());
            propsSet++;
        }
        return propsSet;
    }
}
