/*
 * Copyright (c) 2009-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.Container;
import org.labkey.api.data.FilterInfo;
import org.labkey.api.data.Sort;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.data.xml.queryCustomView.PropertyName;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * A subset of all of the information about a custom view. Split out because in some cases the full info
 * is expensive to retrieve, and some usages only require this subset that is faster to make available.
 *
 * User: klum
 * Date: Jun 17, 2009
 */
public interface CustomViewInfo
{
    public String FILTER_PARAM_PREFIX = "filter";
    public String CONTAINER_FILTER_NAME = "containerFilterName";
    public String AGGREGATE_PARAM_PREFIX = "agg";

    enum ColumnProperty
    {
        columnTitle(PropertyName.COLUMN_TITLE);

        private PropertyName.Enum _xmlEnum;

        private ColumnProperty(PropertyName.Enum xmlEnum)
        {
            _xmlEnum = xmlEnum;
        }

        public PropertyName.Enum getXmlPropertyEnum()
        {
            return _xmlEnum;
        }

        public static ColumnProperty getForXmlEnum(PropertyName.Enum xmlEnum)
        {
            // There's only one possible value right now... once we add more, turn this into a loop or map lookup
            return columnTitle.getXmlPropertyEnum() == xmlEnum ? columnTitle : null;
        }
    }

    public static class FilterAndSort
    {
        private List<FilterInfo> filter = new ArrayList<>();
        private List<Sort.SortField> sort = new ArrayList<>();
        private List<String> containerFilterNames = Collections.emptyList();
        private List<Aggregate> aggregates = new ArrayList<>();

        public List<FilterInfo> getFilter()
        {
            return filter;
        }

        public List<Sort.SortField> getSort()
        {
            return sort;
        }

        public List<String> getContainerFilterNames()
        {
            return containerFilterNames;
        }

        public List<Aggregate> getAggregates()
        {
            return aggregates;
        }

        public static FilterAndSort fromString(String strFilter) throws URISyntaxException
        {
            FilterAndSort fas = new FilterAndSort();

            if (strFilter != null)
            {
                URLHelper filterSort = new URLHelper(strFilter);

                for (String key : filterSort.getKeysByPrefix(FILTER_PARAM_PREFIX + "."))
                {
                    String param = key.substring(FILTER_PARAM_PREFIX.length() + 1);
                    String[] parts = StringUtils.splitPreserveAllTokens(param, '~');

                    if (parts.length != 2)
                        continue;

                    for (String value : filterSort.getParameters(key))
                    {
                        FilterInfo filter = new FilterInfo(parts[0], parts[1], value);
                        fas.filter.add(filter);
                    }
                }

                Sort sort = new Sort(filterSort, FILTER_PARAM_PREFIX);
                fas.sort = sort.getSortList();
                fas.containerFilterNames = filterSort.getParameters(FILTER_PARAM_PREFIX + "." + CONTAINER_FILTER_NAME);

                List<Aggregate> aggregates = Aggregate.fromURL(filterSort, FILTER_PARAM_PREFIX);
                fas.aggregates.addAll(aggregates);
                /*
                // XXX: can be replaced by the code in Aggregate.fromURL() ?
                for (String key : filterSort.getKeysByPrefix(FILTER_PARAM_PREFIX + "." + AGGREGATE_PARAM_PREFIX + "."))
                {
                    String fieldKey = key.substring((FILTER_PARAM_PREFIX + "." + AGGREGATE_PARAM_PREFIX).length() + 1);
                    for (String aggType : filterSort.getParameters(key))
                    {
                        FieldKey fk = FieldKey.fromString(fieldKey);
                        String fieldKeyDecoded = StringUtils.join(fk.getParts(), "/");
                        Aggregate agg = new Aggregate(fieldKeyDecoded, Aggregate.Type.valueOf(aggType.toUpperCase()));
                        fas.aggregates.add(agg);
                    }
                }
                */
            }

            return fas;
        }
    }

    /** Get the name of the custom view or null if this is the default view. */
    @Nullable String getName();

    /**
     * Get the alternate label of the default custom view.
     * Issue 17710. Support an alterante label on the default custom view.
     * The default custom view returns "default" or a customized label set in a file-based module.
     * All other implementations should simply return name for now.
     */
    @NotNull String getLabel();

    /** Get the owner of the custom view or null if this is a shared view. */
    @Nullable User getOwner();

    /** Convenience for <code>getOwner() == null</code> */
    boolean isShared();

    /** Get the user that created the custom view or null if this is an auto-generated view (insert, update) or a file-based custom view. */
    @Nullable User getCreatedBy();

    /** Get the date that the custom view was created or the last modified date for file-based custom views. */
    @NotNull Date getCreated();

    /** Get the user that last modified the custom view  or null if this is an auto-generated view (insert, update) or a file-based custom view. */
    @Nullable User getModifiedBy();

    /** Get the date that the custom view was last modified or the last modified date for file-based custom views. */
    @NotNull Date getModified();

    /** Get the SchemaKey encoded schema name this custom view is bound to. Use {#getSchemaPath} in favor of this method. */
    @NotNull String getSchemaName();

    /** Get the SchemaKey this custom view is bound to. */
    @NotNull SchemaKey getSchemaPath();

    /** Get the query name this custom view is bound to. */
    @NotNull String getQueryName();

    /** Get the container the custom view is defined in or null for file-based custom views. */
    @Nullable Container getContainer();

    /** Get the entityid of this custom view or null if this is an auto-generated view (insert, update) or a file-based custom view. */
    @Nullable String getEntityId();

    /** Returns true if this custom view is inheritable (available in sub-containers). */
    boolean canInherit();

    /** Returns true if this custom view is hidden. */
    boolean isHidden();

    /** Returns true if this custom view is editable. */
    boolean isEditable();

    /** Returns true if this custom view can be overridden through the UI. This is normally true to database views and only true for file-based views that opt-in. */
    boolean isOverridable();

    /** Returns true if the custom view is in session state. */
    boolean isSession();

    /** Get the webapp relative image URL. */
    @Nullable String getCustomIconUrl();

    /** Get the list of columns in the custom view. */
    @NotNull List<FieldKey> getColumns();

    /** Get the list of {@link ColumnProperty} properties. */
    @NotNull List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> getColumnProperties();

    /**
     * Get the URL encoded filter, sort, and aggregates or null.
     * @see CustomViewInfo.FilterAndSort#fromString(String)
     */
    @Nullable String getFilterAndSort();

    /** Get the ContainerFilter name or null. */
    @Nullable String getContainerFilterName();

    /** Returns true if the custom view has filters, sorts, or aggregates. */
    boolean hasFilterOrSort();

}
