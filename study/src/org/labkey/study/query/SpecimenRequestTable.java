package org.labkey.study.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.FieldKey;
import org.labkey.study.StudySchema;

import java.util.List;
import java.util.ArrayList;

/**
 * User: brittp
 * Date: Apr 20, 2007
 * Time: 2:55:18 PM
 */
public class SpecimenRequestTable extends StudyTable
{
    public SpecimenRequestTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSampleRequest());

        AliasedColumn rowIdColumn = new AliasedColumn(this, "RequestId", _rootTable.getColumn("RowId"));
        rowIdColumn.setKeyField(true);
        addColumn(rowIdColumn);
        AliasedColumn statusColumn = new AliasedColumn(this, "Status", _rootTable.getColumn("StatusId"));
        statusColumn.setFk(new LookupForeignKey(null, (String) null, "RowId", "Label")
        {
            public TableInfo getLookupTableInfo()
            {
                return new RequestStatusTable(_schema);
            }
        });
        statusColumn.setKeyField(true);
        addColumn(statusColumn);

        AliasedColumn destinationColumn = new AliasedColumn(this, "Destination", _rootTable.getColumn("DestinationSiteId"));
        destinationColumn.setFk(new LookupForeignKey(null, (String) null, "RowId", "Label")
        {
            public TableInfo getLookupTableInfo()
            {
                return new SiteTable(_schema);
            }
        });
        destinationColumn.setKeyField(true);
        addColumn(destinationColumn);
        addWrapColumn(_rootTable.getColumn("StatusId")).setIsHidden(true);
        addWrapColumn(_rootTable.getColumn("Comments"));
        // there are links to filter by 'createdby' in the UI; it's necessary that this column always
        // be available, so we set it as a key field.
        addWrapColumn(_rootTable.getColumn("CreatedBy")).setKeyField(true);
        addWrapColumn(_rootTable.getColumn("Created"));
        addWrapColumn(_rootTable.getColumn("ModifiedBy"));
        addWrapColumn(_rootTable.getColumn("Modified"));
        ColumnInfo hiddenColumn = addWrapColumn(_rootTable.getColumn("Hidden"));
        hiddenColumn.setIsHidden(true);
        hiddenColumn.setIsUnselectable(true);

        List<FieldKey> fieldKeys = new ArrayList<FieldKey>();
        fieldKeys.add(FieldKey.fromParts("RequestId"));
        fieldKeys.add(FieldKey.fromParts("Status"));
        fieldKeys.add(FieldKey.fromParts("Destination"));
        fieldKeys.add(FieldKey.fromParts("CreatedBy"));
        fieldKeys.add(FieldKey.fromParts("Created"));
        setDefaultVisibleColumns(fieldKeys);
    }
}
