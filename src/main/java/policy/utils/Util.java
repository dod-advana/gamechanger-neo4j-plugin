package policy.utils;

import com.fasterxml.jackson.databind.JsonNode;
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

    public static class Outgoing {
        public Number nodesCreated;
        public Number propertiesSet;
        public Number relationshipsCreated;

        public Outgoing(int nodesCreated, int relationshipsCreated, int propertiesSet) {
            this.nodesCreated = nodesCreated;
            this.relationshipsCreated = relationshipsCreated;
            this.propertiesSet = propertiesSet;
        }
    }
}
