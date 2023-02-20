/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.api.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AnalyticsProviderItem;
import org.labkey.api.data.FilterInfo;
import org.labkey.api.data.Sort;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Callback for implementations that are interested to know when a user-defined {@link CustomView} is being modified.
 * User: kevink
 * Date: 4/17/13
 */
public interface CustomViewChangeListener
{
    // issue 17760 - looking for expected fieldKey parents in the rename case, todo: where to put this list to keep it updated
    List<String> EXPECTED_PARENT_FKS = Arrays.asList("DataSets", "DataSet", "ParticipantVisit");

    void viewCreated(CustomView view);
    void viewChanged(CustomView view);
    void viewDeleted(CustomView view);
    Collection<String> viewDependents(CustomView view);

    static boolean updateCustomViewFieldKeyChange(CustomView customView, @Nullable Map<String, String> queryNameChangeMap, @Nullable Map<String, String> columnNameChangeMap) throws URISyntaxException
    {
        boolean hasUpdates = false;

        // update custom view column list based on fieldKey parts
        boolean columnsUpdated = false;
        List<Map.Entry<FieldKey, Map<CustomViewInfo.ColumnProperty, String>>> updatedColumnProperties = new ArrayList<>();
        for (Map.Entry<FieldKey, Map<CustomViewInfo.ColumnProperty, String>> columnPropertyMap : customView.getColumnProperties())
        {
            FieldKey origFieldKey = columnPropertyMap.getKey();
            FieldKey newFieldKey = getUpdatedFieldKeyReference(origFieldKey, queryNameChangeMap, columnNameChangeMap);
            if (newFieldKey != null)
            {
                Map<CustomViewInfo.ColumnProperty, String> props = columnPropertyMap.getValue();
                updatedColumnProperties.add(Pair.of(newFieldKey, props));
                columnsUpdated = true;
            }
            else
            {
                updatedColumnProperties.add(columnPropertyMap);
            }
        }
        if (columnsUpdated)
        {
            customView.setColumnProperties(updatedColumnProperties);
            hasUpdates = true;
        }

        CustomViewInfo.FilterAndSort fas = CustomViewInfo.FilterAndSort.fromString(customView.getFilterAndSort());
        ActionURL updatedFilterAndSortUrl = new ActionURL();

        // update filter info list based on fieldKey parts, and include them in the updated FilterAndSort URL
        boolean filtersUpdated = false;
        for (FilterInfo filterInfo : fas.getFilter())
        {
            FieldKey origFieldKey = filterInfo.getField();
            FieldKey newFieldKey = getUpdatedFieldKeyReference(origFieldKey, queryNameChangeMap, columnNameChangeMap);
            if (newFieldKey != null)
            {
                filtersUpdated = true;
            }

            filterInfo.applyToURL(updatedFilterAndSortUrl, CustomViewInfo.FILTER_PARAM_PREFIX, newFieldKey != null ? newFieldKey : origFieldKey);
        }

        // update sort field list based on fieldKey parts, and include them in the updated FilterAndSort URL
        boolean sortsUpdated = false;
        Sort sort = new Sort();
        for (Sort.SortField sortField : fas.getSort())
        {
            FieldKey origFieldKey = sortField.getFieldKey();
            FieldKey newFieldKey = getUpdatedFieldKeyReference(origFieldKey, queryNameChangeMap, columnNameChangeMap);
            if (newFieldKey != null)
            {
                sortsUpdated = true;
            }

            sort.appendSortColumn(newFieldKey != null ? newFieldKey : origFieldKey, sortField.getSortDirection(), true);
        }
        sort.applyToURL(updatedFilterAndSortUrl, CustomViewInfo.FILTER_PARAM_PREFIX, false);

        // update analyticsProviders based on fieldKey parts, and include them in the updated FilterAndSort URL
        boolean analyticsProvidersUpdated = false;
        for (AnalyticsProviderItem analyticsProvider : fas.getAnalyticsProviders())
        {
            FieldKey origFieldKey = analyticsProvider.getFieldKey();
            FieldKey newFieldKey = getUpdatedFieldKeyReference(origFieldKey, queryNameChangeMap, columnNameChangeMap);
            if (newFieldKey != null)
                analyticsProvidersUpdated = true;

            analyticsProvider.applyToURL(updatedFilterAndSortUrl, CustomViewInfo.FILTER_PARAM_PREFIX, newFieldKey != null ? newFieldKey : origFieldKey);
        }

        // add the container filters to the updated FilterAndSort URL
        for (String containerFilterName : fas.getContainerFilterNames())
        {
            if (containerFilterName != null)
                updatedFilterAndSortUrl.addParameter(CustomViewInfo.FILTER_PARAM_PREFIX + "." + CustomViewInfo.CONTAINER_FILTER_NAME, containerFilterName);
        }

        if (filtersUpdated || sortsUpdated || analyticsProvidersUpdated)
        {
            customView.setFilterAndSortFromURL(updatedFilterAndSortUrl, CustomViewInfo.FILTER_PARAM_PREFIX);
            hasUpdates = true;
        }

        return hasUpdates;

    }

    static FieldKey getUpdatedFieldKeyReference(FieldKey col, @Nullable Map<String, String> queryNameChangeMap, @Nullable Map<String, String> columnNameChangeMap)
    {
        if (queryNameChangeMap != null && !queryNameChangeMap.isEmpty())
        {
            List<String> keyParts = new ArrayList<>();
            keyParts.add(col.getName());

            // we don't have to worry about field keys without parents (i.e. column/field names without lookup)
            FieldKey currentParent = col.getParent();
            while (currentParent != null)
            {
                // look through the parts of the field key in search of something that matches a query name change
                // and has an expected parent (i.e. Datasets, ParticipantVisit, etc.)
                FieldKey nextParent = currentParent.getParent();
                if (null != nextParent && EXPECTED_PARENT_FKS.contains(nextParent.getName()) && queryNameChangeMap.containsKey(currentParent.getName()))
                {
                    return FieldKey.fromParts(new FieldKey(nextParent, queryNameChangeMap.get(currentParent.getName())), FieldKey.fromParts(keyParts));
                }
                else
                {
                    keyParts.add(0, currentParent.getName());
                }
                currentParent = nextParent;
            }
        }
        else if (columnNameChangeMap != null && !columnNameChangeMap.isEmpty())
        {
            if (columnNameChangeMap.containsKey(col.getName()))
                return FieldKey.fromParts(columnNameChangeMap.get(col.getName()));
        }
        return null;
    }
}
