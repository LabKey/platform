package org.labkey.experiment.api.data;

import org.labkey.api.data.JdbcType;

/**
 * Include only direct children (depth 1)
 *
 * <code>
 * SELECT
 *   d.*
 * FROM assay.General.MyAssay.Data d
 * WHERE
 ExpChildOf(d.resultsample.LSID, 'urn:lsid:labkey.com:Data.Folder-123:1aec9396-3fa2-1038-86f4-495e1672e522') OR ExpDirectChildOf(d.Run.runsample.LSID, 'urn:lsid:labkey.com:Data.Folder-123:1aec9396-3fa2-1038-86f4-495e1672e522')
 * </code>
 */

public class ImmediateChildOfMethod extends ChildOfMethod
{
    public static final String NAME = "ExpDirectChildOf";

    @Override
    protected int getDepth()
    {
        return 1;
    }
}
