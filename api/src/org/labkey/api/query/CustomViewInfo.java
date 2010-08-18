/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.FilterInfo;
import org.labkey.api.data.Sort;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.data.xml.queryCustomView.PropertyName;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jun 17, 2009
 */
public interface CustomViewInfo
{
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
        protected static final String FILTER_PARAM_PREFIX = "filter";
        protected static final String CONTAINER_FILTER_NAME = "containerFilterName";

        private List<FilterInfo> filter = new ArrayList<FilterInfo>();
        private List<Sort.SortField> sort = new ArrayList<Sort.SortField>();
        private String[] containerFilterNames = new String[]{};

        public List<FilterInfo> getFilter()
        {
            return filter;
        }

        public List<Sort.SortField> getSort()
        {
            return sort;
        }

        public String[] getContainerFilterNames()
        {
            return containerFilterNames;
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
            }

            return fas;
        }
    }

    String getName();
    User getOwner();
    /** Convenience for <code>getOwner() == null</code> */
    boolean isShared();
    User getCreatedBy();
    Date getModified();

    String getSchemaName();
    String getQueryName();
    
    Container getContainer();
    boolean canInherit();
    boolean isHidden();
    boolean isEditable();
    /** @returns true if the custom view is in session state. */
    boolean isSession();
    String getCustomIconUrl();

    List<FieldKey> getColumns();
    List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> getColumnProperties();

    String getFilterAndSort();

    String getContainerFilterName();
    
    boolean hasFilterOrSort();

}
