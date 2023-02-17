package org.json;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsonorg.PackageVersion;
import org.json.old.JSONArrayDeserializer;
import org.json.old.JSONArraySerializer;
import org.json.old.JSONObjectDeserializer;
import org.json.old.JSONObjectSerializer;

// A temporary module to support serialization/deserialization of our deprecated JSONObject and JSONArray classes
// TODO: Remove this once migration is complete
@Deprecated
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
        addDeserializer(org.json.old.JSONArray.class, JSONArrayDeserializer.instance);
        addDeserializer(org.json.old.JSONObject.class, JSONObjectDeserializer.instance);
        addSerializer(JSONArraySerializer.instance);
        addSerializer(JSONObjectSerializer.instance);

        // For now, we add serializers & deserializers for the new org.json.* classes here. (Attempting to register both
        // JsonOrgOldModule and JsonOrgModule caused strange problems with serialization... perhaps because these modules
        // aren't given explicit names?) Once migration is complete, go back to registering JsonOrgModule.
        addDeserializer(org.json.JSONArray.class, com.fasterxml.jackson.datatype.jsonorg.JSONArrayDeserializer.instance);
        addDeserializer(org.json.JSONObject.class, com.fasterxml.jackson.datatype.jsonorg.JSONObjectDeserializer.instance);
        addSerializer(com.fasterxml.jackson.datatype.jsonorg.JSONArraySerializer.instance);
        addSerializer(com.fasterxml.jackson.datatype.jsonorg.JSONObjectSerializer.instance);
    }
}
