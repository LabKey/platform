package org.labkey.api.assay;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public class AssayResultsFileWriter<ContextType extends AssayRunUploadContext<? extends AssayProvider>> extends AssayFileWriter<ContextType>
{
    private String FILE_INPUT_NAME = "resultsFile";

    ExpProtocol _protocol;
    ExpRun _run;
    String _pipelineJobGUID;

    public AssayResultsFileWriter(ExpProtocol protocol, @Nullable ExpRun run, @Nullable String pipelineJobGUID)
    {
        _protocol = protocol;
        _run = run;
        _pipelineJobGUID = pipelineJobGUID;
    }

    public static String getRunResultsFileDir(ExpRun run)
    {
        return AssayFileWriter.DIR_NAME + File.separator + "AssayId_" + run.getProtocol().getRowId() + File.separator + "RunId_" + run.getRowId();
    }

    public static String getPipelineResultsFileDir(ExpProtocol protocol, String pipelineJobGUID)
    {
        return AssayFileWriter.DIR_NAME + File.separator + "AssayId_" + protocol.getRowId() + File.separator + "Job_" + pipelineJobGUID;
    }

    @Override
    protected File getFileTargetDir(ContextType context) throws ExperimentException
    {
        String dir = getFileTargetDirName();
        return ensureUploadDirectory(context.getContainer(), dir);
    }

    private String getFileTargetDirName()
    {
        return _run != null ? getRunResultsFileDir(_run) : getPipelineResultsFileDir(_protocol, _pipelineJobGUID);
    }

    private Path resolveDirName(ContextType context, String dirName)
    {
        PipeRoot root = getPipelineRoot(context.getContainer());
        return root.resolveToNioPath(dirName);
    }

    public void cleanupPostedFiles(ContextType context) throws ExperimentException
    {
        try
        {
            Path targetDir = resolveDirName(context, getFileTargetDirName());
            if (targetDir != null && Files.exists(targetDir))
                FileUtil.deleteDir(targetDir);
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
    }

    public Map<String, File> savePostedFiles(ContextType context) throws ExperimentException, IOException
    {
        // if the file results dir already exists, delete it (clean up from previous failed import)
        cleanupPostedFiles(context);

        // if this is a background import and the files have been stashed in the pipeline results dir, move them to the assay results dir
        if (_run != null && _run.getJobId() != null)
        {
            String pipelineJobGUID = PipelineService.get().getJobGUID(context.getUser(), context.getContainer(), _run.getJobId());
            Path pipelineResultsDir = pipelineJobGUID != null ? resolveDirName(context, getPipelineResultsFileDir(_protocol, pipelineJobGUID)) : null;
            if (pipelineResultsDir != null && Files.exists(pipelineResultsDir))
            {
                Path targetDir = resolveDirName(context, getFileTargetDirName());
                FileUtils.moveDirectory(pipelineResultsDir.toFile(), targetDir.toFile()); // TODO will .toFile() work here for cloud?
                return Collections.emptyMap();
            }
        }

        return super.savePostedFiles(context, Collections.singleton(FILE_INPUT_NAME), true);
    }
}