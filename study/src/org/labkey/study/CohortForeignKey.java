package org.labkey.study;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.NullColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.util.StringUtils;
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
    final String _labelCaption;


    public CohortForeignKey(StudyQuerySchema schema)
    {
        this(schema, StudyManager.getInstance().showCohorts(schema.getContainer(), schema.getUser()), null);
    }

    public CohortForeignKey(StudyQuerySchema schema, boolean showCohorts)
    {
        this(schema, StudyManager.getInstance().showCohorts(schema.getContainer(), schema.getUser()), null);
    }

    public CohortForeignKey(StudyQuerySchema schema, boolean showCohorts, String labelCaption)
    {
        assert showCohorts == StudyManager.getInstance().showCohorts(schema.getContainer(), schema.getUser());
        _schema = schema;
        _showCohorts = showCohorts;
        _labelCaption = labelCaption;
    }


    @Override
    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        ColumnInfo c = super.createLookupColumn(parent, displayField);
        if (null == c)
            return null;

        if (!_showCohorts)
            c = new NullColumnInfo(parent.getParentTable(), c.getFieldKey(), c.getJdbcType());

        if (c.getFieldKey().getName().equalsIgnoreCase("Label") && !StringUtils.isEmpty(_labelCaption))
            c.setLabel(_labelCaption);
        return c;
    }


    public TableInfo getLookupTableInfo()
    {
        return new CohortTable(_schema);
    }
}
