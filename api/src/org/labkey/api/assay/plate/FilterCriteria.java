/*
 * Copyright (c) 2024-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.assay.plate;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
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
            var propertyId = criterion.getPropertyId();

            if (propertyId != null)
            {
                if (propertyId == 0)
                    throw new ValidationException(errorMessage(referencePropertyName, i, "Invalid \"propertyId\" value."));
                else if (propertyId < 0)
                    propertyId = null;
            }

            String name = StringUtils.trimToNull(criterion.getName());

            if (propertyId == null)
            {
                // Attempt to resolve the field by name
                if (name != null)
                {
                    if (name.equalsIgnoreCase(referencePropertyName))
                    {
                        propertyId = referencePropertyId;
                        name = referencePropertyName;
                    }
                    else if (replicateStatsDomain != null)
                    {
                        var property = replicateStatsDomain.getPropertyByName(name);
                        if (property != null)
                        {
                            propertyId = property.getPropertyId();
                            name = property.getName();
                        }
                    }

                    if (propertyId == null)
                        throw new ValidationException(errorMessage(referencePropertyName, i, String.format("Unable to resolve field from name \"%s\".", name)));
                }
            }
            else
            {
                if (propertyId == referencePropertyId)
                    name = referencePropertyName;
                else if (replicateStatsDomain != null)
                {
                    var property = replicateStatsDomain.getProperty(propertyId);
                    if (property == null)
                        throw new ValidationException(errorMessage(referencePropertyName, i, "Invalid \"propertyId\" value. Cannot specify criteria against other fields."));
                }
            }

            if (propertyId == null)
            {
                propertyId = referencePropertyId;
                name = referencePropertyName;
            }

            String operation = StringUtils.trimToNull(criterion.getOp());
            if (operation == null)
                throw new ValidationException(errorMessage(referencePropertyName, i, "An \"op\" (operation) property is required."));
            if (CompareType.getByURLKey(operation) == null)
                throw new ValidationException(errorMessage(referencePropertyName, i, String.format("\"%s\" is not a valid operation.", operation)));

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
