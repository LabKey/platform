package org.labkey.study.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.study.StudySchema;
import org.labkey.study.query.studydesign.DefaultStudyDesignTable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by klum on 12/17/13.
 */
public class StudyPersonnelTable extends DefaultStudyDesignTable
{
    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts("Container"));
        defaultVisibleColumns.add(FieldKey.fromParts("Label"));
        defaultVisibleColumns.add(FieldKey.fromParts("Role"));
        defaultVisibleColumns.add(FieldKey.fromParts("URL"));
        defaultVisibleColumns.add(FieldKey.fromParts("UserId"));
    }

    public StudyPersonnelTable(Domain domain, DbSchema dbSchema, UserSchema schema)
    {
        super(domain, dbSchema, schema);

        setName(StudyQuerySchema.PERSONNEL_TABLE_NAME);
        setDescription("Contains one row per each study personnel");
    }

    @Override
    protected void initColumn(ColumnInfo col)
    {
        if ("UserId".equalsIgnoreCase(col.getName()))
        {
            UserIdForeignKey.initColumn(col);
        }
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }
}
