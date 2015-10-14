/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.TableType;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.sql.Query;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
    private LinkedSchema _schema;

    public LinkedSchemaQueryDefinition(LinkedSchema schema, QueryDefinition query)
    {
        super(schema.getUser(), schema.getContainer(), ((QueryDefinitionImpl)query).getQueryDef());
        _schema = schema;
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

    @NotNull
    @Override
    public UserSchema getSchema()
    {
        return _schema;
    }

    @Override
    public Query getQuery(@NotNull QuerySchema schema, List<QueryException> errors, Query parent, boolean includeMetadata)
    {
        // Parse/resolve the wrapped query in the context of the original source schema
        UserSchema sourceSchema = _schema.getSourceSchema();
        return super.getQuery(sourceSchema, errors, parent, includeMetadata);
    }

    @Override
    protected TableInfo applyQueryMetadata(UserSchema schema, List<QueryException> errors, Query query, AbstractTableInfo ret)
    {
        // First, apply original wrapped query-def's metadata from files (using original schema name and container)
        // Second, super.applyQueryMetadata() will also apply orignal wrapped query-def's metadata stored in the database (using original schema name and container)
        UserSchema sourceSchema = _schema.getSourceSchema();
        super.applyQueryMetadata(sourceSchema, errors, query, ret);

        // Third, remove column URLs and some lookups using LinkedTableInfo
        ret = new LinkedTableInfo(_schema, ret).init();
        ret.setDetailsURL(AbstractTableInfo.LINK_DISABLER);

        // Fourth, apply linked schema metadata (either from template or from the linked schema instance)
        TableType metadata = _schema.getXbTable(getName());
        if (metadata != null || (_schema._namedFilters != null && _schema._namedFilters.size() > 0))
            super.applyQueryMetadata(schema, errors, metadata, _schema._namedFilters, ret);

        // Fifth, lookup any XML metadata that has been stored in the database (in linked schema container)
        ret.overlayMetadata(getName(), schema, errors);

        return ret;
    }

    @Override
    public Collection<QueryChangeListener.QueryPropertyChange> save(User user, Container container) throws SQLException
    {
        throw new UnsupportedOperationException("Linked schema queries are read-only!");
    }

    @Override
    public Collection<QueryChangeListener.QueryPropertyChange> save(User user, Container container, boolean fireChangeEvent) throws SQLException
    {
        throw new UnsupportedOperationException("Linked schema queries are read-only!");
    }

    @Override
    public void delete(User user) throws SQLException
    {
        throw new UnsupportedOperationException("Linked schema queries are read-only!");
    }

    @Override
    protected QueryDef edit()
    {
        throw new UnsupportedOperationException("Linked schema queries are read-only!");
    }

    @Override
    public void setCanInherit(boolean f)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setIsHidden(boolean f)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setIsTemporary(boolean temporary)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDescription(String description)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSql()
    {
        return getQueryDef().getSql();
    }

    @Override
    public void setSql(String sql)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMetadataXml(String xml)
    {
        throw new UnsupportedOperationException();
    }

    public void setContainer(Container container)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setContainerFilter(ContainerFilter containerFilter)
    {
        // XXX: Maybe allow?
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSqlEditable()
    {
        return false;
    }

    @Override
    public boolean isMetadataEditable()
    {
        return false;
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
