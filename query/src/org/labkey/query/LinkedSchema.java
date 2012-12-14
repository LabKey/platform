package org.labkey.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.data.xml.TableType;
import org.labkey.query.persist.ExternalSchemaDef;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * User: kevink
 * Date: 12/10/12
 */
public class LinkedSchema extends ExternalSchema
{
    public static void register()
    {
        DefaultSchema.registerProvider(new DefaultSchema.DynamicSchemaProvider()
        {
            @Override
            public QuerySchema getSchema(User user, Container container, String name)
            {
                if (name.equals("LinkedLists"))
                    return LinkedSchema.get(user, container);

                return null;
            }

            @NotNull
            @Override
            public Collection<String> getSchemaNames(User user, Container container)
            {
                return Collections.singleton("LinkedLists");
            }
        });
    }

    private final UserSchema _sourceSchema;

    public static LinkedSchema get(User user, Container container)
    {
        Container parentContainer = container.getParent();
        if (parentContainer == null || parentContainer.isRoot())
            return null;

        ExternalSchemaDef def = new ExternalSchemaDef();
        def.setDbSchemaName("lists");
        def.setUserSchemaName("LinkedLists");

        return get(user, parentContainer, def);
    }

    public static LinkedSchema get(User user, Container container, ExternalSchemaDef def)
    {
        UserSchema sourceSchema = getSourceSchema(def, user, container);
        if (sourceSchema == null)
            return null;

        Map<String, TableType> metaDataMap = new CaseInsensitiveHashMap<TableType>();

        return new LinkedSchema(user, container, def, sourceSchema, metaDataMap, sourceSchema.getTableNames(), Collections.<String>emptySet());
    }

    private static UserSchema getSourceSchema(ExternalSchemaDef def, User user, Container container)
    {
        SchemaKey sourceSchemaName = SchemaKey.fromString(def.getDbSchemaName());
        Container sourceContainer = container; //def.getSourceContainer();
        User sourceUser = user; //def.getSourceUser();

        UserSchema sourceSchema = QueryService.get().getUserSchema(sourceUser, sourceContainer, sourceSchemaName);
        return sourceSchema;
    }

    private LinkedSchema(User user, Container container, ExternalSchemaDef def, UserSchema sourceSchema,
                         Map<String, TableType> metaDataMap, Collection<String> availableTables, Collection<String> hiddenTables)
    {
        //super(def.getUserSchemaName(), "Contains data tables from the '" + def.getUserSchemaName() + "' linked schema.",
        //        user, container, sourceSchema.getDbSchema(), availableTables, hiddenTables);
        super(user, container, def, sourceSchema.getDbSchema(), metaDataMap, availableTables, hiddenTables);

        _sourceSchema = sourceSchema;
    }

    @Override
    protected TableInfo createTable(String name)
    {
        TableInfo table = super.createTable(name);

        // fixup FKs, URLs

        return table;
    }

    @Override
    protected TableInfo createSourceTable(String name)
    {
        return _sourceSchema.getTable(name);
    }

    @Override
    protected TableInfo createWrappedTable(String name, @NotNull TableInfo sourceTable)
    {
        assert !(sourceTable instanceof SchemaTableInfo) : "LinkedSchema only wraps query TableInfos, not SchemaTableInfos";
        return new LinkedTableInfo(this, sourceTable);
    }

}
