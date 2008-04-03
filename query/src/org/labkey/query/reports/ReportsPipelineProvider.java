package org.labkey.query.reports;

import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineStatusFile;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jun 20, 2007
 */
public class ReportsPipelineProvider extends PipelineProvider
{
    static String NAME = "reports";

    public ReportsPipelineProvider()
    {
        super(NAME);
    }

    public void preDeleteStatusFile(PipelineStatusFile sf) throws StatusUpdateException
    {
        // clean up all the temp files on status file deletion
        File filePath = new File(sf.getFilePath());
        if (filePath.exists())
        {
            File dir = filePath.getParentFile();
            for (File file : dir.listFiles())
                file.delete();
            dir.delete();
        }
    }
}
