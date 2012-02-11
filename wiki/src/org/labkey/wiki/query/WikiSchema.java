package org.labkey.wiki.query;

import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.EnumTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: jeckels
 * Date: Feb 9, 2012
 */
public class WikiSchema extends UserSchema
{
    private static final Set<String> TABLE_NAMES;

    static
    {
        Set<String> names = new TreeSet<String>();
        names.add(WikiService.RENDERER_TYPE_TABLE_NAME);
        TABLE_NAMES = Collections.unmodifiableSet(names);
    }

    static public void register()
    {
        DefaultSchema.registerProvider(WikiService.SCHEMA_NAME, new DefaultSchema.SchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new WikiSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public WikiSchema(User user, Container container)
    {
        super(WikiService.SCHEMA_NAME, "Contains information about wiki pages", user, container, CommSchema.getInstance().getSchema());
    }

    @Override
    protected TableInfo createTable(String name)
    {
        if (WikiService.RENDERER_TYPE_TABLE_NAME.equalsIgnoreCase(name))
        {
            EnumTableInfo<WikiRendererType> result = new EnumTableInfo<WikiRendererType>(WikiRendererType.class, CommSchema.getInstance().getSchema(), "Contains the type of renderers available to format content");
            result.setPublicSchemaName(getName());
            return result;
        }
        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        return TABLE_NAMES;
    }
}
