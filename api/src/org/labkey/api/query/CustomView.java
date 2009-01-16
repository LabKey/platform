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

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

public interface CustomView
{
    enum ColumnProperty
    {
        columnTitle
    }

    QueryDefinition getQueryDefinition();
    String getName();
    User getOwner();
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
}
