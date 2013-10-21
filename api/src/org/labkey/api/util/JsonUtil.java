package org.labkey.api.util;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Helper methods for parsing JSON objects using Jackson.
 */
public class JsonUtil
{
    public static JsonLocation expectObjectStart(JsonParser p) throws IOException
    {
        if (p.getCurrentToken() != JsonToken.START_OBJECT)
            throw new JsonParseException("Expected object start '{', got '" + p.getCurrentToken() + "'", p.getTokenLocation());

        JsonLocation loc = p.getTokenLocation();
        p.nextToken();
        return loc;
    }

    public static void expectObjectEnd(JsonParser p) throws IOException
    {
        if (p.getCurrentToken() != JsonToken.END_OBJECT)
            throw new JsonParseException("Expected object end '}', got '" + p.getCurrentToken() + "'", p.getTokenLocation());

        p.nextToken();
    }

    public static JsonLocation expectArrayStart(JsonParser p) throws IOException
    {
        if (p.getCurrentToken() != JsonToken.START_ARRAY)
            throw new JsonParseException("Expected array start '[', got '" + p.getCurrentToken() + "'", p.getTokenLocation());

        JsonLocation loc = p.getTokenLocation();
        p.nextToken();
        return loc;
    }

    public static void expectArrayEnd(JsonParser p) throws IOException
    {
        if (!isArrayEnd(p))
            throw new JsonParseException("Expected array end ']', got '" + p.getCurrentToken() + "'", p.getTokenLocation());

        p.nextToken();
    }

    public static boolean isArrayEnd(JsonParser p) throws IOException
    {
        return p.getCurrentToken() == JsonToken.END_ARRAY;
    }

    public static void skipValue(JsonParser p) throws IOException
    {
        p.skipChildren();
        p.nextToken();
    }

    public static Object valueOf(JsonNode n) throws JsonParseException
    {
        if (!n.isValueNode())
            throw new JsonParseException("Expected value node", null);

        if (n.isNull())
            return null;

        if (n.isNumber())
            return n.numberValue();

        if (n.isBoolean())
            return n.booleanValue();

        if (n.isTextual())
            return n.textValue();

        throw new JsonParseException("Unexpected value type: " + n.getNodeType(), null);
    }
}
