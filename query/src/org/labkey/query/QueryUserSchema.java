package org.labkey.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.query.query.CustomViewsTable;
import org.labkey.query.query.QueryDbSchema;

import java.util.HashSet;
import java.util.Set;

public class QueryUserSchema extends UserSchema
{
    public static final String SCHEMA_NAME = "query";
    public static final String SCHEMA_DESCR = "Contains query related data.";

    public static final String CUSTOM_VIEWS_TABLE_NAME = "CustomViews";

    static public void register(final Module module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module)
        {
            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new QueryUserSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public QueryUserSchema(User user, Container container)
    {
        super(SCHEMA_NAME, SCHEMA_DESCR, user, container, QueryDbSchema.getInstance().getSchema());
    }

    @Override
    public Set<String> getTableNames()
    {
        Set<String> names = new HashSet<>();

        if (getContainer().hasPermission(getUser(), AdminPermission.class))
            names.add(CUSTOM_VIEWS_TABLE_NAME);

        return names;
    }

    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
    {
        if (CUSTOM_VIEWS_TABLE_NAME.equalsIgnoreCase(name) && getContainer().hasPermission(getUser(), AdminPermission.class))
            return new CustomViewsTable(this, cf);

        return null;
    }
}
