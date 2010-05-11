/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.StringExpression;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.List;
import java.sql.SQLException;

public interface QueryDefinition
{
    String getName();
    String getSchemaName();

    Container getContainer();
    boolean canInherit();
    void setCanInherit(boolean f);
    boolean isHidden();
    void setIsHidden(boolean f);
    boolean isSnapshot();

    CustomView getCustomView(User user, HttpServletRequest request, String name);
    Map<String, CustomView> getCustomViews(User user, HttpServletRequest request);
    CustomView createCustomView(User user, String name);
    List<ColumnInfo> getColumns(CustomView view, TableInfo table);
    List<DisplayColumn> getDisplayColumns(CustomView view, TableInfo table);

    /**
     * Return a tableInfo representing this query.
     * @param includeMetadata
     */
    TableInfo getTable(QuerySchema schema, List<QueryException> errors, boolean includeMetadata);
    TableInfo getMainTable();

    String getSql();
    String getMetadataXml();
    String getDescription();
    void setDescription(String description);

    void setSql(String sql);
    void setMetadataXml(String xml);
    void setContainer(Container container);
    void setContainerFilter(ContainerFilter containerFilter);
    ContainerFilter getContainerFilter();

    boolean canEdit(User user);
    void save(User user, Container container) throws SQLException;
    void delete(User user) throws SQLException;

    List<QueryException> getParseErrors(QuerySchema schema);

    ActionURL urlFor(QueryAction action);
    ActionURL urlFor(QueryAction action, Container container);
    StringExpression urlExpr(QueryAction action, Container container);
    UserSchema getSchema();

    /**
     * Returns whether this is a table-based query definition (versus a custom query).
     */
    boolean isTableQueryDefinition();

    boolean isMetadataEditable();
    ViewOptions getViewOptions();
}
