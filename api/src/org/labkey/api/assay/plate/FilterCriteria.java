package org.labkey.api.assay.plate;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.gwt.client.model.GWTFilterCriteria;
import org.labkey.api.query.ValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record FilterCriteria(
    String operation,
    String value,
    Integer propertyId,
    @Nullable String name,
    Integer referencePropertyId,
    Integer domainId
)
{
    public static @NotNull List<FilterCriteria> fromGWTFilterCriteria(
        List<GWTFilterCriteria> filterCriteria,
        int referencePropertyId,
        String referencePropertyName,
        int domainId,
        @Nullable Domain replicateStatsDomain
    ) throws ValidationException
    {
        if (filterCriteria == null || filterCriteria.isEmpty())
            return Collections.emptyList();

        // Invariants
        if (referencePropertyId <= 0)
            throw new IllegalArgumentException("A valid \"referencePropertyId\" must be specified for filter criteria.");
        if (StringUtils.trimToNull(referencePropertyName) == null)
            throw new IllegalArgumentException("A valid \"referencePropertyName\" must be specified for filter criteria.");
        if (domainId <= 0)
            throw new IllegalArgumentException("A valid \"domainId\" must be specified for filter criteria.");

        var criteria = new ArrayList<FilterCriteria>();

        for (int i = 0; i < filterCriteria.size(); i++)
        {
            var criterion = filterCriteria.get(i);
            Integer propertyId = criterion.getPropertyId();

            if (propertyId != null && propertyId <= 0)
                throw new ValidationException(errorMessage(referencePropertyName, i, "Invalid \"propertyId\" value."));

            String name = StringUtils.trimToNull(criterion.getName());

            // Attempt to resolve the field by name
            if (propertyId == null && name != null)
            {
                if (replicateStatsDomain != null)
                {
                    var property = replicateStatsDomain.getPropertyByName(name);
                    if (property == null)
                        throw new ValidationException(errorMessage(referencePropertyName, i, String.format("Unable to resolve field from name \"%s\".", name)));

                    propertyId = property.getPropertyId();
                }
                else if (name.equalsIgnoreCase(referencePropertyName))
                {
                    propertyId = referencePropertyId;
                    name = referencePropertyName;
                }
            }
            else if (propertyId != null && propertyId != referencePropertyId)
                throw new ValidationException(errorMessage(referencePropertyName, i, "Invalid \"propertyId\" value. Cannot specify criteria against other fields."));

            if (propertyId == null)
            {
                propertyId = referencePropertyId;
                name = referencePropertyName;
            }

            String operation = StringUtils.trimToNull(criterion.getOp());
            if (operation == null)
                throw new ValidationException(errorMessage(referencePropertyName, i, "An \"op\" (operation) property is required."));

            String value = criterion.getValue() == null ? null : criterion.getValue().toString();
            criteria.add(new FilterCriteria(operation, value, propertyId, name, referencePropertyId, domainId));
        }

        return criteria;
    }

    public static List<GWTFilterCriteria> toGWTFilterCriteria(List<FilterCriteria> criteria)
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
            filterCriterion.setReferencePropertyId(criterion.referencePropertyId);
            filterCriterion.setValue(criterion.value);

            filterCriteria.add(filterCriterion);
        }

        return filterCriteria;
    }

    private static String errorMessage(String referencePropertyName, int index, String message)
    {
        return String.format("Invalid hit criteria for field \"%s\" at index [%d]. %s", referencePropertyName, index, message);
    }
}
