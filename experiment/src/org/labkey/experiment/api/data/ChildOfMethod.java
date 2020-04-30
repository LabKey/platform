package org.labkey.experiment.api.data;

import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AbstractMethodInfo;

/**
 * <code>
 * SELECT
 *   d.*
 * FROM assay.General.MyAssay.Data d
 * WHERE
    ExpChildOf(d.resultsample.LSID, 'urn:lsid:labkey.com:Data.Folder-123:1aec9396-3fa2-1038-86f4-495e1672e522') OR ExpChildOf(d.Run.runsample.LSID, 'urn:lsid:labkey.com:Data.Folder-123:1aec9396-3fa2-1038-86f4-495e1672e522')
 * </code>
 */

public class ChildOfMethod extends AbstractMethodInfo
{
    public static final String NAME = "ExpChildOf";

    public ChildOfMethod()
    {
        super(JdbcType.BOOLEAN);
    }

    @Override
    public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
    {
        SQLFragment fieldKeyFrag = arguments[0];
        SQLFragment lsidFrag = arguments[1];
        return LineageHelper.createInSQL(fieldKeyFrag, lsidFrag, LineageHelper.createChildOfOptions(getDepth()));
    }

    protected int getDepth()
    {
        return 0;
    }

}
