package org.labkey.xarassay;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewURLHelper;

import java.io.File;
import java.sql.SQLException;

/**
 * User: phussey
 * Date: Sep 19, 2007
 * Time: 11:35:02 AM
 */
public class XarAssayPipelineJob extends PipelineJob
{

    public XarAssayPipelineJob(ViewBackgroundInfo info, File logFile) throws SQLException
    {
        super(XarAssayPipelineProvider.name, info);
        setLogFile(logFile);
    }

    public ViewURLHelper getStatusHref()
    {
        // No custom viewing for status while loading
        return null;
    }

    public String getDescription()
    {
        return "Assay load from Xar";
    }
    public void run()
    {
        setStatus(PipelineJob.COMPLETE_STATUS);
    }



}
