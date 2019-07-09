/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.labkey.api.writer.VirtualFile;
import org.springframework.validation.Errors;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Additional information about a custom view beyond what's exposed in {@link CustomViewInfo}.
 */
public interface CustomView extends CustomViewInfo
{
    QueryDefinition getQueryDefinition();
    void setName(String name);
    void setQueryName(String queryName);
    void setCanInherit(boolean f);
    boolean canEdit(Container c, Errors errors);
    void setIsHidden(boolean f);
    void setColumns(List<FieldKey> columns);
    void setColumnProperties(List<Map.Entry<FieldKey, Map<ColumnProperty,String>>> list);

    void applyFilterAndSortToURL(ActionURL url, String dataRegionName);
    void setFilterAndSortFromURL(ActionURL url, String dataRegionName);

    void setFilterAndSort(String filter);

    void save(User user, HttpServletRequest request) throws QueryException;
    void delete(User user, HttpServletRequest request) throws QueryException;
    /** @return true if serialized successfully. */
    boolean serialize(VirtualFile dir) throws IOException;

    Collection<String> getDependents(User user);

    default List<String> getErrors()
    {
        return null;
    }
}
