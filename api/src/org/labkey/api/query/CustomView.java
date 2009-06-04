/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.VirtualFile;
import org.labkey.data.xml.queryCustomView.PropertyName;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.io.IOException;

public interface CustomView
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

    QueryDefinition getQueryDefinition();
    String getName();
    User getOwner();
    User getCreatedBy();

    Container getContainer();
    boolean canInherit();
    void setCanInherit(boolean f);
    boolean isHidden();
    void setIsHidden(boolean f);
    boolean isEditable();
    String getCustomIconUrl();

    List<FieldKey> getColumns();
    List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> getColumnProperties();
    void setColumns(List<FieldKey> columns);
    void setColumnProperties(List<Map.Entry<FieldKey, Map<ColumnProperty,String>>> list);

    void applyFilterAndSortToURL(ActionURL url, String dataRegionName);
    void setFilterAndSortFromURL(ActionURL url, String dataRegionName);
    String getFilter();
    void setFilter(String filter);
    String getContainerFilterName();
    
    boolean hasFilterOrSort();

    void save(User user, HttpServletRequest request) throws QueryException;
    void delete(User user, HttpServletRequest request) throws QueryException;
    void serialize(VirtualFile dir) throws IOException;
}
