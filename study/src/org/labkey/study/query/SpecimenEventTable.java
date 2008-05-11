package org.labkey.study.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.study.StudySchema;

import java.util.List;
import java.util.ArrayList;

/**
 * User: brittp
 * Date: Jan 26, 2007
 * Time: 9:49:46 AM
 */
public class SpecimenEventTable extends StudyTable
{
    private static final String[] DEFAULT_VISIBLE_COLS = {
    "LabId",
    "Stored",
    "StorageFlag",
    "StorageDate",
    "ShipFlag",
    "ShipBatchNumber",
    "ShipDate",
    "LabReceiptDate",
    "SpecimenCondition",
    "Comments",
    "fr_container",
    "fr_level1",
    "fr_level2",
    "fr_position",
    "freezer"
    };

    public SpecimenEventTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenEvent());

        for (ColumnInfo baseColumn : _rootTable.getColumnsList())
        {
            String name = baseColumn.getName();
            if ("Container".equalsIgnoreCase(name) ||
                "RowId".equalsIgnoreCase(name) ||
                "ScharpId".equalsIgnoreCase(name))
                continue;

            if (getColumn(name) == null)
                addWrapColumn(baseColumn);
        }

        List<FieldKey> defaultVisible = new ArrayList<FieldKey>();
        for (String col : DEFAULT_VISIBLE_COLS)
            defaultVisible.add(new FieldKey(null, col));
        setDefaultVisibleColumns(defaultVisible);

        getColumn("LabId").setCaption("Location");
    }
}
