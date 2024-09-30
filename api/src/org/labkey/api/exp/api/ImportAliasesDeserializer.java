package org.labkey.api.exp.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.micrometer.common.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ImportAliasesDeserializer extends StdDeserializer<Map<String, Map<String, Object>>>
{
    protected ImportAliasesDeserializer()
    {
        super(Map.class);
    }

    @Override
    public Map<String, Map<String, Object>> deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException
    {
        JsonNode node = jsonParser.readValueAsTree();
        if (node == null)
            return null;

        if (node.isObject() && !node.isEmpty())
        {
            Map<String, Map<String, Object>> aliases = new HashMap<>();
            for (Map.Entry<String, JsonNode> alias : node.properties())
            {
                String aliasName = alias.getKey();
                JsonNode aliasNode = alias.getValue();
                String inputType = null;
                boolean required = false;
                if (aliasNode.isObject())
                {
                    inputType = aliasNode.get("inputType").asText();
                    required = aliasNode.get("required") != null && aliasNode.get("required").asBoolean();
                }
                else if (aliasNode.isValueNode())
                {
                    // legacy importAliases: {"LabParent":"dataInputs/Lab","BloodParent":"materialInputs/Blood"}
                    inputType = aliasNode.asText();
                }

                if (!StringUtils.isEmpty(inputType))
                    aliases.put(aliasName, Map.of("inputType", inputType, "required", required));
            }

            return aliases;
        }

        return null;
    }
}
