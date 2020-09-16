package org.labkey.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProviderSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.security.User;
import org.labkey.assay.plate.TsvPlateTypeHandler;
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
            return new PlateTemplateTable(this, cf);
        }
        return super.createTable(name, cf);
    }

    private class PlateTemplateTable extends FilteredTable<TsvProviderSchema>
    {
        public PlateTemplateTable(TsvProviderSchema schema, ContainerFilter cf)
        {
            super(AssayDbSchema.getInstance().getTableInfoPlate(), schema, cf);

            var column = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Lsid")));
            column.setKeyField(true);

            var containerCol = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Container")));
            ContainerForeignKey.initColumn(containerCol, schema);

            addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Name")));
            addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Type")));
            addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Created")));
            addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("CreatedBy")));
            addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Modified")));
            addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("ModifiedBy")));
            addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Rows")));
            addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Columns")));

            addCondition(new SimpleFilter(FieldKey.fromParts("Type"), TsvPlateTypeHandler.TYPE));

            // need to override the title column on the base table
            setTitleColumn("Name");

            setDefaultVisibleColumns(List.of(
                    FieldKey.fromParts("Name"),
                    FieldKey.fromParts("Type"),
                    FieldKey.fromParts("Rows"),
                    FieldKey.fromParts("Columns")
            ));
        }
    }
}
