package org.labkey.api.assay;

import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpRun;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class AssayResultsFileWriter<ContextType extends AssayRunUploadContext<? extends AssayProvider>> extends AssayFileWriter<ContextType>
{
    private String FILE_INPUT_NAME = "resultsFile";

    ExpRun _run;

    public AssayResultsFileWriter(ExpRun run)
    {
        _run = run;
    }

    public static String getRunResultsFileDir(ExpRun run)
    {
        return AssayFileWriter.DIR_NAME + File.separator + "AssayId_" + run.getProtocol().getRowId() + File.separator + "RunId_" + run.getRowId();
    }

    @Override
    protected File getFileTargetDir(ContextType context) throws ExperimentException
    {
        String dir = getRunResultsFileDir(_run);
        return ensureUploadDirectory(context.getContainer(), dir);
    }

    public Map<String, File> savePostedFiles(ContextType context) throws ExperimentException, IOException
    {
        return super.savePostedFiles(context, Collections.singleton(FILE_INPUT_NAME), true);
    }
}