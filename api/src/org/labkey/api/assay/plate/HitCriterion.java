package org.labkey.api.assay.plate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.query.ValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record HitCriterion(String operation, String value, @Nullable Integer propertyId, @Nullable String name, Integer referencePropertyId, Integer domainId)
{
    public static @NotNull List<HitCriterion> getCriteriaFromJSON(JSONArray jsonArray, int referencePropertyId, String referencePropertyName, int domainId) throws ValidationException
    {
        if (jsonArray == null || jsonArray.isEmpty())
            return Collections.emptyList();

        var criteria = new ArrayList<HitCriterion>();

        for (int i = 0; i < jsonArray.length(); i++)
        {
            var entry = jsonArray.get(i);

            if (!(entry instanceof JSONObject json))
                throw new ValidationException(errorMessage(referencePropertyName, i, "JSON array contains invalid elements."));

            boolean hasPropertyId = json.has("propertyId");
            boolean hasName = json.has("name");

            if (!hasPropertyId && !hasName)
                throw new ValidationException(errorMessage(referencePropertyName, i, "Either a \"propertyId\" or \"name\" is required."));
            if (!json.has("op"))
                throw new ValidationException(errorMessage(referencePropertyName, i, "An \"op\" (operation) property is required."));

            try
            {
                Integer propertyId = hasPropertyId ? json.getInt("propertyId") : null;
                String name = hasName ? json.getString("name") : null;
                String operation = json.getString("op");
                Object value = json.has("value") ? json.get("value") : null;

                criteria.add(new HitCriterion(operation, value == null ? null : value.toString(), propertyId, name, referencePropertyId, domainId));
            }
            catch (JSONException e)
            {
                throw new ValidationException(errorMessage(referencePropertyName, i, e.getMessage()));
            }
        }

        return criteria;
    }

    public static JSONArray toJSON(List<HitCriterion> criteria)
    {
        var json = new JSONArray();

        for (HitCriterion criterion : criteria)
        {
            var object = new JSONObject();

            object.put("name", criterion.name);
            object.put("op", criterion.operation);
            object.put("propertyId", criterion.propertyId);
            object.put("value", criterion.value);
            // Intentionally not serializing "ReferencePropertyId"
            // Intentionally not serializing "DomainId"

            json.put(object);
        }

        return json;
    }

    private static String errorMessage(String parentName, int index, String message)
    {
        return String.format("Invalid hit criteria for field \"%s\" at index [%d]. %s", parentName, index, message);
    }
}
