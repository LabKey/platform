package org.labkey.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.query.persist.QueryDef;

import java.sql.SQLException;
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
        super(schema.getUser(), ((QueryDefinitionImpl)query).getQueryDef());
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
    public Container getContainer()
    {
        return _schema.getContainer();
    }

    @Override
    public void save(User user, Container container) throws SQLException
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

    @Override
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
    public ActionURL urlFor(QueryAction action)
    {
        // Disallow all table URLs
        return null;
    }

    @Override
    public ActionURL urlFor(QueryAction action, Container container)
    {
        // Disallow all table URLs
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
