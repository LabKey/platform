package org.labkey.api.util;

import com.fasterxml.jackson.databind.module.SimpleModule;

import com.fasterxml.jackson.datatype.jsonorg.PackageVersion;
import org.json.old.JSONArray;
import org.json.old.JSONObject;

// A temporary module to support serialization/deserialization of our deprecated JSONObject and JSONArray classes
// TODO: Remove this once migration is complete
public class JsonOrgOldModule extends SimpleModule
{
    private static final long serialVersionUID = 1;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    public JsonOrgOldModule()
    {
        super(PackageVersion.VERSION);
        addDeserializer(JSONArray.class, JSONArrayDeserializer.instance);
        addDeserializer(JSONObject.class, JSONObjectDeserializer.instance);
        addSerializer(JSONArraySerializer.instance);
        addSerializer(JSONObjectSerializer.instance);
    }
}
