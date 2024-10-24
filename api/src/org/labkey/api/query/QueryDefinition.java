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

import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryChangeListener.QueryProperty;
import org.labkey.api.query.QueryChangeListener.QueryPropertyChange;
import org.labkey.api.security.User;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface QueryDefinition
{
    String getName();
    void setName(String name);
    String getTitle();

    @Deprecated /** Use #getSchemaPath() instead. */
    String getSchemaName();
    SchemaKey getSchemaPath();

    /** Returns the User this QueryDefinition was initialized with (ie, from the ViewContext).  QueryDefinitions do not have an owner. */
    User getUser();
    /** Returns the Container from which this QueryDefinition was initialized (may be different from getDefinitionContainer() for inherited queries). */
    Container getContainer();
    /** Returns the Container this QueryDef was defined in. */
    Container getDefinitionContainer();
    boolean canInherit();
    void setCanInherit(boolean f);
    boolean isHidden();
    void setIsHidden(boolean f);
    boolean isSnapshot();
    void setIsTemporary(boolean temporary);
    boolean isTemporary();

    boolean isIncludedForLookups();
    void setIsIncludedForLookups(boolean included);

    /**
     * Get the named custom view for this QueryDefinition and owner.
     * @param owner Get the custom view with the given name owned by the user or is shared.
     * @param request It not null, include custom views saved in session state.
     * @param name The name of the custom view.
     * @return The CustomView.
     */
    CustomView getCustomView(@NotNull User owner, @Nullable HttpServletRequest request, String name);

    /**
     * Get the shared custom view for this QueryDefinition by name.
     * @param name The name of the custom view.
     * @return The shared CustomView.
     */
    CustomView getSharedCustomView(String name);

    /**
     * Get all custom views applicable to this query.
     *
     * @param owner If not null, get custom views owned by the user and shared views.  If null, gets all custom views from all owners and shared views.
     * @param request If not null, include custom views saved in session state.
     * @param includeHidden If true, include hidden custom views.
     * @param sharedOnly If true, return only shared custom views.
     * @param excludeSessionView If true, return only non session custom views.
     */
    Map<String, CustomView> getCustomViews(@Nullable User owner, @Nullable HttpServletRequest request, boolean includeHidden, boolean sharedOnly, boolean excludeSessionView);

    Map<String, CustomView> getCustomViews(@Nullable User owner, @Nullable HttpServletRequest request, boolean includeHidden, boolean sharedOnly);

    CustomView createCustomView();
    CustomView createCustomView(@NotNull User owner, String name);
    CustomView createSharedCustomView(String name);

    List<ColumnInfo> getColumns(CustomView view, TableInfo table);
    List<DisplayColumn> getDisplayColumns(CustomView view, TableInfo table);

    /**
     * Return a tableInfo representing this query.
     *
     * CONSIDER remove setContainerFilter() and add parameter to getTable(UserSchema, ContainerFilter...)
     */
    @Nullable TableInfo getTable(List<QueryException> errors, boolean includeMetadata);
    @Nullable TableInfo getTable(UserSchema schema, List<QueryException> errors, boolean includeMetadata);

    /**
     * Use skipSuggestedColumns=TRUE to skip adding suggested/referenced columns.
     * Issue 36275: ODBC/Tableau generates select query containing columns from referenced table
     */
    @Nullable TableInfo getTable(UserSchema schema, List<QueryException> errors, boolean includeMetadata, boolean skipSuggestedColumns);

    String getSql();
    String getMetadataXml();
    TablesDocument getMetadataTablesDocument();
    String getDescription();
    String getModuleName();

    void setDescription(String description);

    void setSql(String sql);
    void setMetadataXml(String xml);
    void setDefinitionContainer(Container container);
    void setContainerFilter(ContainerFilter containerFilter);
    ContainerFilter getContainerFilter();

    /**
     * Returns true if the user has permission to edit the metadata for this query in the current container (not the definition container.)
     * @see #isSqlEditable()
     */
    boolean canEdit(User user);

    /**
     * Returns true if the user has permission to edit the metadata for this query in the current container (not the definition container.)
     * @see #isMetadataEditable()
     */
    boolean canEditMetadata(User user);

    boolean canDelete(User user);

    /**
     * Save a new QueryDefinition or update an existing QueryDefinition.
     * TableQueryDefinition and file-based queries cannot be deleted.
     * Fires the {@link QueryChangeListener#queryChanged(User, Container, ContainerFilter, SchemaKey, QueryProperty, Collection)} event.
     */
    Collection<QueryPropertyChange> save(User user, Container container) throws SQLException;
    Collection<QueryPropertyChange> save(User user, Container container, boolean fireChangeEvent) throws SQLException;

    /**
     * Delete the QueryDefinition.
     * TableQueryDefinition and file-based queries cannot be deleted.
     * Fires the {@link QueryChangeListener#queryDeleted(User, Container, ContainerFilter, SchemaKey, Collection)} event.
     */
    void delete(User user) throws SQLException;
    void delete(User user, boolean fireChangeEvent) throws SQLException;

    List<QueryParseException> getParseErrors(QuerySchema schema);
    boolean validateQuery(QuerySchema schema, List<QueryParseException> errors, List<QueryParseException> warnings);

    @Nullable ActionURL urlFor(QueryAction action);
    /** Used for URLs that don't require row-level info, like insert or grid URLs */
    @Nullable ActionURL urlFor(QueryAction action, Container container);
    /** Used for URLs that require row-level info, like details or update URLs */
    @Nullable ActionURL urlFor(QueryAction action, Container container, Map<String, Object> pkValues);
    @Nullable StringExpression urlExpr(QueryAction action, Container container);
    @NotNull UserSchema getSchema();
    void setSchema(@NotNull UserSchema schema);

    /**
     * Return true if this user-defined custom query definition (as oppossed to a generated wrapper over a built-in table or linked schema query.)
     */
    boolean isUserDefined();
    Collection<String> getDependents(User user);

    /**
     * Returns true if the sql source for this query is editable.
     * This is a capability check, not a permissions check.
     * @see #canEdit(User)
     */
    boolean isSqlEditable();

    /**
     * Returns true if the metadata for this query is editable.
     * This is a capability check, not a permissions check.
     * @see #canEditMetadata(User)
     */
    boolean isMetadataEditable();
    ViewOptions getViewOptions();
    void setMetadataTableMap(Map<String, TableType> metadataTableMap);

    /**
     * Return a query key that is based on the schema path and name of the QueryDefinition.
     */
    String getQueryKey();
}
