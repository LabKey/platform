/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

    @Deprecated // Use .getSchemaPath() instead.
    String getSchemaName();
    SchemaKey getSchemaPath();

    /** Returns the User this QueryDefinition was initialized with (ie, from the ViewContext).  QueryDefinitions do not have an owner. */
    User getUser();
    /** Returns the Container this QueryDefinition was defined in. */
    Container getContainer();
    boolean canInherit();
    void setCanInherit(boolean f);
    boolean isHidden();
    void setIsHidden(boolean f);
    boolean isSnapshot();
    void setIsTemporary(boolean temporary);
    boolean isTemporary();

    CustomView getCustomView(@Nullable User owner, @Nullable HttpServletRequest request, String name);
    Map<String, CustomView> getCustomViews(@Nullable User owner, @Nullable HttpServletRequest request, boolean includeHidden);
    CustomView createCustomView(@Nullable User owner, String name);
    List<ColumnInfo> getColumns(CustomView view, TableInfo table);
    List<DisplayColumn> getDisplayColumns(CustomView view, TableInfo table);

    /**
     * Return a tableInfo representing this query.
     */
    TableInfo getTable(List<QueryException> errors, boolean includeMetadata);
    TableInfo getTable(UserSchema schema, List<QueryException> errors, boolean includeMetadata);
    TableInfo getMainTable();

    String getSql();
    String getMetadataXml();
    String getDescription();
    String getModuleName();

    void setDescription(String description);

    void setSql(String sql);
    void setMetadataXml(String xml);
    void setContainer(Container container);
    void setContainerFilter(ContainerFilter containerFilter);
    ContainerFilter getContainerFilter();

    boolean canEdit(User user);
    void save(User user, Container container) throws SQLException;
    void delete(User user) throws SQLException;

    List<QueryParseException> getParseErrors(QuerySchema schema);

    @Nullable ActionURL urlFor(QueryAction action);
    /** Used for URLs that don't require row-level info, like insert or grid URLs */
    @Nullable ActionURL urlFor(QueryAction action, Container container);
    /** Used for URLs that require row-level info, like details or update URLs */
    @Nullable ActionURL urlFor(QueryAction action, Container container, Map<String, Object> pkValues);
    @Nullable StringExpression urlExpr(QueryAction action, Container container);
    @NotNull UserSchema getSchema();

    /**
     * Returns whether this is a table-based query definition (versus a custom query).
     */
    boolean isTableQueryDefinition();

    boolean isSqlEditable();
    boolean isMetadataEditable();
    ViewOptions getViewOptions();
}
