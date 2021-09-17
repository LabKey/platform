/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
package org.labkey.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.sql.Query;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: 12/17/12
 *
 * Wrapped QueryDefinition used in a LinkedSchema.
 * The wrapped query has a container of the LinkedSchema instead of the original query's container.
 * In addition the query URLs are all null to indicate that they are disabled.
 */
public class LinkedSchemaQueryDefinition extends QueryDefinitionImpl
{
    private final List<QueryException> _sourceQueryErrors;
    private String _extraMetadata;

    public LinkedSchemaQueryDefinition(LinkedSchema schema, QueryDefinition query, String extraMetadata)
    {
        super(schema.getUser(), schema.getContainer(), ((QueryDefinitionImpl)query).getQueryDef());
        _schema = schema;
        _extraMetadata = extraMetadata;
        _sourceQueryErrors = null;
    }

    public LinkedSchemaQueryDefinition(LinkedSchema schema, List<QueryException> sourceQueryErrors, String name)
    {
        super(schema.getUser(), schema.getContainer(), schema.getSchemaPath(), name);
        assert !sourceQueryErrors.isEmpty();
        _schema = schema;
        _sourceQueryErrors = sourceQueryErrors;
    }

    @Override
    public String getSchemaName()
    {
        return _schema.getSchemaName();
    }

    @Override
    public SchemaKey getSchemaPath()
    {
        return _schema.getSchemaPath();
    }

    @Override
    public void setSchema(@NotNull UserSchema schema)
    {
        throw new UnsupportedOperationException("Linked schema queries are read-only!");
    }

    @NotNull
    @Override
    public LinkedSchema getSchema()
    {
        return (LinkedSchema)_schema;
    }

    @Override
    public Query getQuery(@NotNull QuerySchema schema, List<QueryException> errors, Query parent, boolean includeMetadata, boolean skipSuggestedColumns)
    {
        // Parse/resolve the wrapped query in the context of the original source schema
        UserSchema sourceSchema = getSchema().getSourceSchema();

        if (_sourceQueryErrors != null && !_sourceQueryErrors.isEmpty())
        {
            if (errors != null)
                errors.addAll(_sourceQueryErrors);
            Query q = new Query(schema, getName(), parent);
            q.setDebugName(getSchemaName() + "." + getName());
            q.getParseErrors().addAll(_sourceQueryErrors);
            return q;
        }

        return super.getQuery(sourceSchema, errors, parent, includeMetadata, skipSuggestedColumns);
    }

    @Override
    protected TableInfo applyQueryMetadata(UserSchema schema, List<QueryException> errors, Query query, AbstractTableInfo ret)
    {
        // First, apply original wrapped query-def's metadata from files (using original schema name and container)
        // Second, super.applyQueryMetadata() will also apply orignal wrapped query-def's metadata stored in the database (using original schema name and container)
        LinkedSchema linkedSchema = getSchema();
        UserSchema sourceSchema = linkedSchema.getSourceSchema();
        super.applyQueryMetadata(sourceSchema, errors, query, ret);

        // Third, remove column URLs and some lookups using LinkedTableInfo
        ret = new LinkedTableInfo(linkedSchema, ret).init();
        ret.setDetailsURL(AbstractTableInfo.LINK_DISABLER);

        // Fourth, apply linked schema metadata (either from template or from the linked schema instance)
        TableType metadata = linkedSchema.getXbTable(getName());
        if (metadata != null || (linkedSchema._namedFilters != null && linkedSchema._namedFilters.size() > 0))
            super.applyQueryMetadata(schema, errors, metadata, linkedSchema._namedFilters, ret);

        // Fifth, lookup any XML metadata that has been stored in the database (in linked schema container)
        ret.overlayMetadata(getName(), schema, errors);

        // Finally, apply any custom XML that might have been passed in from the client. See issue 38903
        if (_extraMetadata != null)
        {
            QueryDef.ParsedMetadata parsedMetadata = new QueryDef.ParsedMetadata(_extraMetadata);
            TablesDocument tablesDocument = parsedMetadata.getTablesDocument(errors);
            if (tablesDocument != null)
            {
                TablesType tables = tablesDocument.getTables();
                if (tables != null && tables.sizeOfTableArray() > 0)
                    ret.overlayMetadata(Collections.singleton(tables.getTableArray()[0]), _schema, errors);
            }
        }

        return ret;
    }

    @Override
    public @Nullable TableInfo createTable(@NotNull UserSchema schema, @Nullable List<QueryException> errors, boolean includeMetadata, @Nullable Query query, boolean skipSuggestedColumns)
    {
        if (_sourceQueryErrors != null && !_sourceQueryErrors.isEmpty())
        {
            if (errors != null)
                errors.addAll(_sourceQueryErrors);
            return null;
        }

        return super.createTable(schema, errors, includeMetadata, query, skipSuggestedColumns);
    }

    @Override
    public Collection<QueryChangeListener.QueryPropertyChange> save(User user, Container container)
    {
        return save(user, container, true);
    }

    @Override
    public Collection<QueryChangeListener.QueryPropertyChange> save(User user, Container container, boolean fireChangeEvent)
    {
        if (!getContainer().equals(container))
            throw new UnauthorizedException("Can only be saved in the linked schema container");

        if (!_dirty)
            return null;

        QueryDef qdef = QueryManager.get().getQueryDef(container, getSchemaName(), getName(), false);
        if (_extraMetadata == null)
        {
            if (qdef != null)
            {
                // delete the query in order to reset the metadata over a built-in query, but don't
                // fire the listener because we haven't actually deleted the table. See issue 40365
                QueryManager.get().delete(qdef);
            }
        }
        else
        {
            if (qdef == null)
            {
                qdef = new QueryDef();
                qdef.setSchema(getSchemaName());
                qdef.setContainer(container.getId());
                qdef.setName(this.getName());
            }
            assert qdef.getSql() == null : "metadata only querydef should not have sql";
            qdef.setMetaData(_extraMetadata);
            if (qdef.getQueryDefId() == 0)
                QueryManager.get().insert(user, qdef);
            else
                QueryManager.get().update(user, qdef);
        }

        if (fireChangeEvent)
        {
            // Fire change event for each property change.
            for (QueryChangeListener.QueryPropertyChange change : _changes)
            {
                QueryService.get().fireQueryChanged(user, container, null, _queryDef.getSchemaPath(), change.getProperty(), Collections.singleton(change));
            }
        }

        Collection<QueryChangeListener.QueryPropertyChange> changes = _changes;
        _changes = null;
        _dirty = false;
        return changes;
    }

    @Override
    public void delete(User user)
    {
        throw new UnsupportedOperationException("Linked schema queries are read-only!");
    }

    @Override
    protected QueryDef edit()
    {
        throw new UnsupportedOperationException("Linked schema queries are read-only!");
    }

    @Override
    public void setName(String name)
    {
        throw new UnsupportedOperationException("Linked schema queries are read-only!");
    }

    @Override
    public void setDefinitionContainer(Container container)
    {
        throw new UnsupportedOperationException("Linked schema queries are read-only!");
    }

    @Override
    public void setCanInherit(boolean f)
    {
        throw new UnsupportedOperationException("Linked schema queries are read-only!");
    }

    @Override
    public void setIsHidden(boolean f)
    {
        throw new UnsupportedOperationException("Linked schema queries are read-only!");
    }

    @Override
    public void setIsTemporary(boolean temporary)
    {
        throw new UnsupportedOperationException("Linked schema queries are read-only!");
    }

    @Override
    public void setIsSnapshot(boolean f)
    {
        throw new UnsupportedOperationException("Linked schema queries are read-only!");
    }

    @Override
    public void setDescription(String description)
    {
        throw new UnsupportedOperationException("Linked schema queries are read-only!");
    }

    @Override
    public String getSql()
    {
        return getQueryDef().getSql();
    }

    @Override
    public void setSql(String sql)
    {
        throw new UnsupportedOperationException("Linked schema queries are read-only!");
    }

    @Override
    public String getMetadataXml()
    {
        return _extraMetadata;
    }

    @Override
    public void setMetadataXml(String xml)
    {
        if (xml == null && _extraMetadata == null)
            return;

        if (_extraMetadata == null || !_extraMetadata.equals(xml))
        {
            _dirty = true;
            _extraMetadata = xml;
        }
    }

    public void setContainer(Container container)
    {
        throw new UnsupportedOperationException("Linked schema queries are read-only!");
    }

    @Override
    public void setContainerFilter(ContainerFilter containerFilter)
    {
        // Container filter is pre-defined by linked schema
    }

    @Override
    public boolean canEdit(User user)
    {
        return false;
    }

    // True if the user is allowed to edit metadata in the current container (not the source definition container)
    @Override
    public boolean canEditMetadata(User user)
    {
        return getContainer().hasPermissions(user, Set.of(EditQueriesPermission.class, UpdatePermission.class));
    }

    @Override
    public boolean canDelete(User user)
    {
        return false;
    }

    @Override
    public boolean canInherit()
    {
        return false;
    }

    // Linked query defs are similar to table custom query defs in that they are generated wrappers over the source
    // schema's custom query so we will consider these as not user defined.
    @Override
    public boolean isUserDefined()
    {
        return false;
    }

    @Override
    public boolean isSqlEditable()
    {
        return false;
    }

    @Override
    public boolean isMetadataEditable()
    {
        return true;
    }

    @Override
    public ActionURL urlFor(QueryAction action, Container container)
    {
        // Allow execute and export URLs
        if (action == QueryAction.executeQuery ||
                action == QueryAction.exportRowsExcel ||
                action == QueryAction.exportRowsXLSX ||
                action == QueryAction.exportRowsTsv ||
                action == QueryAction.printRows)
            return QueryService.get().urlDefault(container, action, getSchemaName(), getName());

        return null;
    }

    @Override
    public ActionURL urlFor(QueryAction action, Container container, Map<String, Object> pkValues)
    {
        // Disallow all table URLs
        return null;
    }

    @Override
    public StringExpression urlExpr(QueryAction action, Container container)
    {
        // Disallow all table URLs
        return null;
    }
}
