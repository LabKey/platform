package org.labkey.experiment.api.property;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.experiment.ExperimentModule;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.util.LinkedHashSet;
import java.util.Set;

public class PropertyUserSchema extends UserSchema
{
    public static final String SCHEMA_NAME = "property";
    public static final SchemaKey SCHEMA_KEY = SchemaKey.fromParts(SCHEMA_NAME);

    public static void register(ExperimentModule module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module) {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                // The 'property' schema is always available
                // TODO: hide if there are no Domains
                return true;
            }

            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new PropertyUserSchema(schema);
            }
        });
    }

    public enum TableType
    {
        Domains
        {
            @Override
            public TableInfo createTable(PropertyUserSchema schema, ContainerFilter cf)
            {
                return schema.createDomainsTable(cf);
            }
        },
        DomainProperties
        {
            @Override
            public TableInfo createTable(PropertyUserSchema schema, ContainerFilter cf)
            {
                return schema.createDomainPropertiesTable(cf);
            }
        }
        ;
        public abstract TableInfo createTable(PropertyUserSchema expSchema, ContainerFilter cf);
    }

    static private Set<String> TABLE_NAMES = new LinkedHashSet<>();

    static
    {
        for (TableType type : TableType.values())
        {
            TABLE_NAMES.add(type.toString());
        }
    }

    public PropertyUserSchema(DefaultSchema schema)
    {
        super(SCHEMA_KEY, "Contains domain and property definitions", schema.getUser(), schema.getContainer(), ExperimentServiceImpl.get().getSchema(), null);
    }

    @Override
    public Set<String> getTableNames()
    {
        return TABLE_NAMES;
    }

    @Override
    public @Nullable TableInfo createTable(String name, ContainerFilter cf)
    {
        for (TableType tableType : TableType.values())
        {
            if (tableType.name().equalsIgnoreCase(name))
            {
                return tableType.createTable(this, cf);
            }
        }

        return null;
    }

    public TableInfo getTable(TableType tableType)
    {
        return getTable(tableType.toString());
    }

    public TableInfo getTable(TableType tableType, ContainerFilter cf)
    {
        return getTable(tableType.toString(), cf);
    }

    public DomainsTableInfo createDomainsTable(ContainerFilter cf)
    {
        return new DomainsTableInfo(this, cf).populateColumns();
    }

    public TableInfo createDomainPropertiesTable(ContainerFilter cf)
    {
        return new DomainPropertiesTableInfo(this, cf).populateColumns();
    }

}
