package org.labkey.study.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.study.StudySchema;

/**
 * User: jeckels
 * Date: Jun 22, 2009
 */
public class VialTable extends BaseStudyTable
{
    public VialTable(final StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoVial());

        addWrapColumn(getRealTable().getColumn("RowID")).setIsHidden(true);
        
        addWrapColumn(getRealTable().getColumn("GlobalUniqueID"));
        addWrapColumn(getRealTable().getColumn("Volume"));
        ColumnInfo specimenCol = wrapColumn("Specimen", getRealTable().getColumn("SpecimenID"));
        specimenCol.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                SimpleSpecimenTable tableInfo = schema.createSimpleSpecimenTable();
                tableInfo.setContainerFilter(ContainerFilter.EVERYTHING);
                return tableInfo;
            }
        });
        addColumn(specimenCol);

        addVialCommentsColumn(false);

        ColumnInfo containerCol = addWrapColumn(getRealTable().getColumn("Container"));
        containerCol.setFk(new ContainerForeignKey());
        containerCol.setIsHidden(true);
    }
}
