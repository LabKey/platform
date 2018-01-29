package org.labkey.study.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.PipelineJobLoggerGetter;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.Pair;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.importer.StudyImportContext;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.springframework.validation.BindException;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileAnalysisDatasetTask extends AbstractDatasetImportTask<FileAnalysisDatasetTask.Factory>
{
    private transient StudyImpl _study = null;
    public static final String ORIGINAL_SOURCE_KEY = "OriginalSourcePath";
    public static final String DATASET_NAME_KEY = "name";
    public static final String DATASET_ID_KEY = "id";

    private FileAnalysisDatasetTask(FileAnalysisDatasetTask.Factory factory, PipelineJob job, StudyImportContext ctx)
    {
        super(factory, job, ctx);
    }

    @Override
    public StudyImpl getStudy()
    {
        if (null == _study)
            _study = getStudyManager().getStudy(getJob().getContainer());
        return _study;
    }

    @Override
    protected String getDatasetsFileName() throws ImportException
    {
        return ".datasets";
    }

    @Override
    protected VirtualFile getDatasetsDirectory() throws ImportException
    {
        FileAnalysisJobSupport jobSupport = getJob().getJobSupport(FileAnalysisJobSupport.class);
        File dataDir = jobSupport.getDataDirectory();
        if (dataDir.exists())
        {
            return new FileSystemFile(dataDir);
        }
        return null;
    }

    @NotNull
    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        try
        {
            FileAnalysisJobSupport jobSupport = getJob().getJobSupport(FileAnalysisJobSupport.class);
            Map<String, String> params = jobSupport.getParameters();

            if (params.containsKey(DATASET_ID_KEY) && params.containsKey(DATASET_NAME_KEY))
            {
                _ctx.getLogger().error("Dataset ID and name cannot be specified at the same time.");
                return new RecordedActionSet();
            }

            Map<File, Pair<String, String>> inputDataMap = new HashMap<>();

            // guaranteed to only have a single file
            assert jobSupport.getInputFiles().size() == 1;
            for (File file : jobSupport.getInputFiles())
            {
                if (params.containsKey(DATASET_ID_KEY))
                    inputDataMap.put(file, new Pair<>(DATASET_ID_KEY, params.get(DATASET_ID_KEY)));
                else if (params.containsKey(DATASET_NAME_KEY))
                    inputDataMap.put(file, new Pair<>(DATASET_NAME_KEY, params.get(DATASET_NAME_KEY)));
                else if (params.containsKey(ORIGINAL_SOURCE_KEY))
                    inputDataMap.put(file, new Pair<>(ORIGINAL_SOURCE_KEY, params.get(ORIGINAL_SOURCE_KEY)));
            }
            List<String> readerErrors = new ArrayList<>();
            StudyImpl study = getStudy();
            DatasetInferSchemaReader reader = new DatasetInferSchemaReader(getDatasetsDirectory(), getStudy(), _ctx, inputDataMap);
            reader.validate(readerErrors);

            for (String error : readerErrors)
                _ctx.getLogger().error(error);

            BindException errors = new NullSafeBindException(new BaseViewAction.BeanUtilsPropertyBindingResult(this, "pipeline"));
            if (StudyManager.getInstance().importDatasetSchemas(study, _ctx.getUser(), reader, errors, _ctx.isCreateSharedDatasets(), _ctx.getActivity()))
            {
                doImport(getDatasetsDirectory(), getDatasetsFileName(), getJob(), _ctx, getStudy(), reader, true);
            }
            return new RecordedActionSet();
        }
        catch (Exception e)
        {
            throw new PipelineJobException("Exception importing datasets", e);
        }
    }

    public static class Factory extends AbstractDatasetImportTaskFactory<FileAnalysisDatasetTask.Factory>
    {
        public Factory()
        {
            super(FileAnalysisDatasetTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            StudyImportContext ctx = new StudyImportContext(job.getUser(), job.getContainer(), null, new PipelineJobLoggerGetter(job));
            return new FileAnalysisDatasetTask(this, job, ctx);
        }
    }
}
