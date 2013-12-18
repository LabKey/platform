package org.labkey.study.query.studydesign;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.study.query.StudyQuerySchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by klum on 12/13/13.
 */
public class StudyTreatmentProductTable extends DefaultStudyDesignTable
{
    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts("Container"));
        defaultVisibleColumns.add(FieldKey.fromParts("TreatmentId"));
        defaultVisibleColumns.add(FieldKey.fromParts("ProductId"));
        defaultVisibleColumns.add(FieldKey.fromParts("Dose"));
        defaultVisibleColumns.add(FieldKey.fromParts("Route"));
    }

    public StudyTreatmentProductTable(Domain domain, DbSchema dbSchema, UserSchema schema)
    {
        super(domain, dbSchema, schema);

        setName(StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);

        ColumnInfo productIdCol = getColumn("ProductId");
        productIdCol.setFk(new LookupForeignKey("RowId")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return QueryService.get().getUserSchema(_userSchema.getUser(), _userSchema.getContainer(), StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.PRODUCT_TABLE_NAME);
            }
        });

        ColumnInfo treatmentIdCol = getColumn("TreatmentId");
        treatmentIdCol.setFk(new LookupForeignKey("RowId")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return QueryService.get().getUserSchema(_userSchema.getUser(), _userSchema.getContainer(), StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_TABLE_NAME);
            }
        });
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }
}
