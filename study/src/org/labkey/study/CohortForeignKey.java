package org.labkey.study;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.NullColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.CohortTable;
import org.labkey.study.query.StudyQuerySchema;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 2012-12-11
 * Time: 9:55 AM
 */
public class CohortForeignKey extends LookupForeignKey
{
    final StudyQuerySchema _schema;
    final boolean _showCohorts;


    public CohortForeignKey(StudyQuerySchema schema)
    {
        this(schema, StudyManager.getInstance().showCohorts(schema.getContainer(), schema.getUser()));
    }


    public CohortForeignKey(StudyQuerySchema schema, boolean showCohorts)
    {
        assert showCohorts == StudyManager.getInstance().showCohorts(schema.getContainer(), schema.getUser());
        _schema = schema;
        _showCohorts = showCohorts;
    }


    @Override
    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        ColumnInfo c = super.createLookupColumn(parent, displayField);
        if (_showCohorts || null == c)
            return c;

        return new NullColumnInfo(parent.getParentTable(), c.getFieldKey(), c.getJdbcType());
    }


    public TableInfo getLookupTableInfo()
    {
        return new CohortTable(_schema);
    }
}
