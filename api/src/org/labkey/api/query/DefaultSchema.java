package org.labkey.api.query;

import org.labkey.api.security.User;
import org.labkey.api.data.*;
import org.labkey.api.util.CaseInsensitiveHashMap;

import java.util.*;

/**
 * A schema, scoped to a particular container
 */
final public class DefaultSchema extends AbstractSchema
{
    static public abstract class SchemaProvider
    {
        abstract public QuerySchema getSchema(DefaultSchema schema);
    }
    static final Map<String, SchemaProvider> _providers = Collections.synchronizedMap(new CaseInsensitiveHashMap<SchemaProvider>());

    static public void registerProvider(String name, SchemaProvider provider)
    {
        _providers.put(name, provider);
    }

    final Map<String, QuerySchema> _schemas;
    static
    {
        registerProvider("Folder", new FolderSchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new FolderSchema(schema.getUser(), schema.getContainer(), null);
            }
        });
        registerProvider("Project", new FolderSchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                Container container = schema.getContainer().getProject();
                return new FolderSchema(schema.getUser(), container, DefaultSchema.get(schema.getUser(), container));
            }
        });
        registerProvider("Shared", new FolderSchemaProvider(){
            public QuerySchema getSchema(DefaultSchema schema)
            {
                Container container = ContainerManager.getSharedContainer();
                return new FolderSchema(schema.getUser(), container, DefaultSchema.get(schema.getUser(), container));
            }
        });
    }
    static public DefaultSchema get(User user, Container container)
    {
        return new DefaultSchema(user, container);
    }

    private DefaultSchema(User user, Container container)
    {
        super(CoreSchema.getInstance().getSchema(), user, container);
        _schemas = new CaseInsensitiveHashMap<QuerySchema>();
    }

    public TableInfo getTable(String name, String alias)
    {
        return null;
    }

    public QuerySchema getSchema(String name)
    {
        QuerySchema ret = _schemas.get(name);
        if (ret != null)
            return ret;
        SchemaProvider provider = _providers.get(name);
        if (provider == null)
        {
            return QueryService.get().getDbUserSchemas(this).get(name);
        }
        return provider.getSchema(this);
    }

    public Set<String> getSchemaNames()
    {
        Set<String> ret = new TreeSet<String>(_providers.keySet());
        ret.addAll(QueryService.get().getDbUserSchemas(this).keySet());
        return Collections.unmodifiableSet(ret);
    }

    public Set<String> getUserSchemaNames()
    {
        Set<String> ret = new TreeSet<String>();
        for (String schemaName : getSchemaNames())
        {
            QuerySchema schema = getSchema(schemaName);
            if (!(schema instanceof UserSchema))
            {
                continue;
            }
            UserSchema userSchema = (UserSchema) schema;
            if (userSchema.getSchemaName() == null)
                continue;
            ret.add(schemaName);
        }
        return ret;
    }

    public Set<String> getTableAndQueryNames()
    {
        return Collections.emptySet();
    }
}
