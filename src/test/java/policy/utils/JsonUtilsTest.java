package policy.utils;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JsonUtilsTest {

    @Test
    public void shouldReturnJsonNodeFromString() throws Throwable {
        JsonNode test = JsonUtils.loadJson("{\"test\": \"test\"}", true);
        assertEquals("These should equal", "test", test.get("test").asText());
    }
}
