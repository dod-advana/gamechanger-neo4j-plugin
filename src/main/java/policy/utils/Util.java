package policy.utils;

import com.fasterxml.jackson.databind.JsonNode;
import org.neo4j.logging.Log;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.Label;
import org.neo4j.internal.helpers.collection.Iterables;

import java.util.*;

public class Util {
    public static final Label[] NO_LABELS = new Label[0];
    public static final String[] EMPTY_ARRAY = new String[0];

    public static Label[] labels(Object labelNames) {
        if (labelNames==null) return NO_LABELS;
        if (labelNames instanceof List) {
            Set names = new LinkedHashSet((List) labelNames); // Removing duplicates
            Label[] labels = new Label[names.size()];
            int i = 0;
            for (Object l : names) {
                if (l==null) continue;
                labels[i++] = Label.label(l.toString());
            }
            if (i <= labels.length) return Arrays.copyOf(labels,i);
            return labels;
        }
        return new Label[]{Label.label(labelNames.toString())};
    }

    public static <T extends Entity> void setProperty(T pc, String key, Object value) {
        if (value == null) pc.removeProperty(key);
        else pc.setProperty(key, toPropertyValue(value));
    }

    public static Object toPropertyValue(Object value) {
        if (value instanceof Iterable) {
            Iterable it = (Iterable) value;
            Object first = Iterables.firstOrNull(it);
            if (first == null) return EMPTY_ARRAY;
            return Iterables.asArray(first.getClass(), it);
        }
        return value;
    }

    public static Relationship createNonDuplicateRelationship(Node fromNode, Node toNode, RelationshipType type, Log log) {
        log.debug(String.format("Creating relationship from node %d to node %d", fromNode.getId(), toNode.getId()));
        log.debug("fromNode relationships:");

        for (Relationship relationship : fromNode.getRelationships(Direction.OUTGOING, type)) {
            Node endNode = relationship.getEndNode();
            log.debug(String.format("\tEnd node: %d", endNode.getId()));
            if (endNode.getId() == toNode.getId()) {
                log.debug("\tRelationship between these nodes already exists, returning null");
                return null;
            }
        }

        log.debug("Creating relationship");
        return fromNode.createRelationshipTo(toNode, type);
    }

    public static class Outgoing {
        public final Number nodesCreated;
        public final Number propertiesSet;
        public final Number relationshipsCreated;

        public Outgoing(int nodesCreated, int relationshipsCreated, int propertiesSet) {
            this.nodesCreated = nodesCreated;
            this.relationshipsCreated = relationshipsCreated;
            this.propertiesSet = propertiesSet;
        }
    }

    public static class NodeRelationshipWrapper {
        public final List<Node> nodes;
        public final List<Relationship> relationships;

        public NodeRelationshipWrapper(List<Node> nodes, List<Relationship> relationships) {
            this.nodes = nodes;
            this.relationships = relationships;
        }
    }

    public static Map<String,Object> map(Object ... values) {
        return Util._map(values);
    }

    private static <T> Map<String, T> _map(T ... values) {
        Map<String, T> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i+=2) {
            if (values[i] == null) continue;
            map.put(values[i].toString(),values[i+1]);
        }
        return map;
    }
}
