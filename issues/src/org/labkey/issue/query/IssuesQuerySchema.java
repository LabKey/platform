package org.labkey.issue.query;

import org.labkey.api.query.UserSchema;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.security.User;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.issues.IssuesSchema;

import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;

public class IssuesQuerySchema extends UserSchema 
{
    public static final String SCHEMA_NAME = "issues";

    public enum TableType
    {
        Issues,
    }
    static private Set<String> tableNames = new LinkedHashSet<String>();
    static
    {
        for (TableType type : TableType.values())
        {
            tableNames.add(type.toString());
        }
        tableNames = Collections.unmodifiableSet(tableNames);
    }

    static public void register()
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider() {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new IssuesQuerySchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public IssuesQuerySchema(User user, Container container)
    {
        super(SCHEMA_NAME, user, container, IssuesSchema.getInstance().getSchema());
    }

    public Set<String> getTableNames()
    {
        return tableNames;
    }

    public TableInfo getTable(String name, String alias)
    {
        if (name != null)
        {
            try
            {
                switch(TableType.valueOf(name))
                {
                    case Issues:
                        return createIssuesTable(alias);
                }
            }
            catch (IllegalArgumentException e){}
        }
        return super.getTable(name, alias);
    }

    public TableInfo createIssuesTable(String alias)
    {
        return new IssuesTable(this);
    }
}
