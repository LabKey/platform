package org.labkey.api.query;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.NavTrailConfig;

import java.util.*;

public interface QuerySchema
{
    public User getUser();

    public Container getContainer();

    public DbSchema getDbSchema();

    public TableInfo getTable(String name, String alias);

    public QuerySchema getSchema(String name);

    public Set<String> getSchemaNames();
}
