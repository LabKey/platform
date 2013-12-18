package org.labkey.study.query.studydesign;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.study.query.StudyQuerySchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by klum on 12/12/13.
 */
public class StudyProductTable extends DefaultStudyDesignTable
{
    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts("Container"));
        defaultVisibleColumns.add(FieldKey.fromParts("Label"));
        defaultVisibleColumns.add(FieldKey.fromParts("Role"));
        defaultVisibleColumns.add(FieldKey.fromParts("Type"));
    }

    public StudyProductTable(Domain domain, DbSchema dbSchema, UserSchema schema)
    {
        super(domain, dbSchema, schema);

        setName(StudyQuerySchema.PRODUCT_TABLE_NAME);
        setDescription("Contains one row per study product");
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }
}
