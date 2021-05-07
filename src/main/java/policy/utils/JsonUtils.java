package policy.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class JsonUtils {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static JsonNode loadJson(String jsonString, boolean failOnError) throws JsonProcessingException {
        try {
            return OBJECT_MAPPER.readTree(jsonString);
        } catch (Exception e) {
            if (!failOnError)
                return OBJECT_MAPPER.readTree("{}");
            else
                throw new RuntimeException("Failed parsing JSON string: " + jsonString + " - "+e.getMessage());
        }
    }

    public static List<String> getStringListFromJsonArray(JsonNode arrayNode) {
        List<String> returnArray = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            returnArray.add(item.asText());
        }
        return returnArray;
    }
}
