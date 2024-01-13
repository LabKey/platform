package org.labkey.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProviderSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.assay.plate.TsvPlateLayoutHandler;
import org.labkey.assay.query.AssayDbSchema;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TsvProviderSchema extends AssayProviderSchema
{
    public static final String PLATE_TEMPLATE_TABLE = "PlateTemplate";

    public TsvProviderSchema(User user, Container container, TsvAssayProvider provider, @Nullable Container targetStudy)
    {
        super(user, container, provider, targetStudy);
    }

    @Override
    public Set<String> getTableNames()
    {
        return Collections.singleton(PLATE_TEMPLATE_TABLE);
    }

    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
    {
        if (name.equalsIgnoreCase(PLATE_TEMPLATE_TABLE))
        {
            return new PlateTemplateTable(this, cf).init();
        }
        return super.createTable(name, cf);
    }

    private static class PlateTemplateTable extends SimpleUserSchema.SimpleTable<TsvProviderSchema>
    {
        public PlateTemplateTable(TsvProviderSchema schema, ContainerFilter cf)
        {
            super(schema, AssayDbSchema.getInstance().getTableInfoPlate(), cf);
            setName(PLATE_TEMPLATE_TABLE);
            setTitleColumn("Name");

            addCondition(new SimpleFilter(FieldKey.fromParts("Type"), TsvPlateLayoutHandler.TYPE));
        }

        @Override
        public void addColumns()
        {
            super.addColumns();

            // Remove the "RowId" field so the "Lsid" is considered the primary key
            removeColumn(getColumn(FieldKey.fromParts("RowId")));
            getMutableColumn(FieldKey.fromParts("Lsid")).setKeyField(true);
        }

        @Override
        public List<FieldKey> getDefaultVisibleColumns()
        {
            return List.of(
                FieldKey.fromParts("Name"),
                FieldKey.fromParts("Type"),
                FieldKey.fromParts("Rows"),
                FieldKey.fromParts("Columns")
            );
        }
    }
}
