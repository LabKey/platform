package org.labkey.api.assay.plate;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.gwt.client.model.GWTFilterCriteria;
import org.labkey.api.query.ValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record HitCriterion(String operation, String value, @Nullable Integer propertyId, @Nullable String name, Integer referencePropertyId, Integer domainId)
{
    public static @NotNull List<HitCriterion> getCriteriaFromGWTFilterCriteria(List<GWTFilterCriteria> filterCriteria, int referencePropertyId, String referencePropertyName, int domainId) throws ValidationException
    {
        if (filterCriteria == null || filterCriteria.isEmpty())
            return Collections.emptyList();

        var criteria = new ArrayList<HitCriterion>();

        for (int i = 0; i < filterCriteria.size(); i++)
        {
            var filterCriterion = filterCriteria.get(i);

            boolean hasValidPropertyId = filterCriterion.getPropertyId() != null && filterCriterion.getPropertyId() > 0;
            String name = StringUtils.trimToNull(filterCriterion.getName());
            String operation = StringUtils.trimToNull(filterCriterion.getOp());

            if (!hasValidPropertyId && name == null)
                throw new ValidationException(errorMessage(referencePropertyName, i, "Either a \"propertyId\" or \"name\" is required."));
            if (operation == null)
                throw new ValidationException(errorMessage(referencePropertyName, i, "An \"op\" (operation) property is required."));

            try
            {
                Integer propertyId = hasValidPropertyId ? filterCriterion.getPropertyId() : null;
                Object value = filterCriterion.getValue() == null ? null : filterCriterion.getValue();

                criteria.add(new HitCriterion(operation, value == null ? null : value.toString(), propertyId, name, referencePropertyId, domainId));
            }
            catch (JSONException e)
            {
                throw new ValidationException(errorMessage(referencePropertyName, i, e.getMessage()));
            }
        }

        return criteria;
    }

    public static List<GWTFilterCriteria> toGWTFilterCriteria(List<HitCriterion> criteria)
    {
        if (criteria == null || criteria.isEmpty())
            return Collections.emptyList();

        var filterCriteria = new ArrayList<GWTFilterCriteria>();

        for (var criterion : criteria)
        {
            var filterCriterion = new GWTFilterCriteria();
            filterCriterion.setName(criterion.name);
            filterCriterion.setOp(criterion.operation);
            filterCriterion.setPropertyId(criterion.propertyId);
            filterCriterion.setValue(criterion.value);
            // Intentionally not serializing "ReferencePropertyId"
            // Intentionally not serializing "DomainId"

            filterCriteria.add(filterCriterion);
        }

        return filterCriteria;
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
