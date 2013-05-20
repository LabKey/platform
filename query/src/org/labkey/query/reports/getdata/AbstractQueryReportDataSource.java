package org.labkey.query.reports.getdata;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;

/**
 * User: jeckels
 * Date: 5/15/13
 */
public abstract class AbstractQueryReportDataSource implements QueryReportDataSource
{
    private final User _user;
    private final Container _container;
    private final SchemaKey _schemaKey;

    private UserSchema _schema;

    public AbstractQueryReportDataSource(User user, Container container, SchemaKey schemaKey)
    {
        _user = user;
        _container = container;
        _schemaKey = schemaKey;
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


}
