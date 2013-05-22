package org.labkey.query.reports.getdata;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;

import java.util.Map;

/**
 * Base class for query-based (SQL and schema/query) data sources for GetData API. Most of the config can be handled
 * by the base class.
 *
 * User: jeckels
 * Date: 5/15/13
 */
public abstract class AbstractQueryReportDataSource implements QueryReportDataSource
{
    @NotNull private final User _user;
    @NotNull private final Container _container;
    @NotNull private final SchemaKey _schemaKey;
    @Nullable protected final ContainerFilter _containerFilter;
    @NotNull protected final Map<String, String> _parameters;

    private UserSchema _schema;

    public AbstractQueryReportDataSource(@NotNull User user, @NotNull Container container, @NotNull SchemaKey schemaKey, @Nullable ContainerFilter containerFilter, @NotNull Map<String, String> parameters)
    {
        _user = user;
        _container = container;
        _schemaKey = schemaKey;
        _containerFilter = containerFilter;
        _parameters = parameters;
    }

    @NotNull
    public UserSchema getSchema()
    {
        if (_schema == null)
        {
            _schema = QueryService.get().getUserSchema(_user, _container, _schemaKey);
            if (_schema == null)
            {
                throw new NotFoundException("Could not resolve schema " + _schemaKey + " in container " + _container.getPath());
            }
        }
        return _schema;
    }

    protected abstract QueryDefinition createBaseQueryDef();

    @Override
    public final QueryDefinition getQueryDefinition()
    {
        QueryDefinition queryDef = createBaseQueryDef();
        if (_containerFilter != null)
        {
            queryDef.setContainerFilter(_containerFilter);
        }
        return queryDef;
    }
}
