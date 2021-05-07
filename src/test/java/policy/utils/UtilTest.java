package policy.utils;

import org.junit.Test;
import org.neo4j.graphdb.Label;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class UtilTest {

    @Test
    public void shouldCreateSingleLabel() {
        Label[] test = new Label[]{Label.label("Test")};
        List<String> labels = new ArrayList<>(Arrays.asList("Test"));
        Label[] actual = Util.labels(labels);
        assertEquals("Should only be one label and it should match", test, actual);
    }

    @Test
    public void shouldCreateMultipleLabels() {
        Label[] test = new Label[]{Label.label("Test"), Label.label("Test2")};
        List<String> labels = new ArrayList<>(Arrays.asList("Test", "Test2"));
        Label[] actual = Util.labels(labels);
        assertEquals("Should only be two labels and it should match", test, actual);
    }
}
