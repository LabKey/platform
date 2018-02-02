/*
 * Copyright (c) 2006-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.study.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.ImportException;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.util.Path;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.StudyImportContext;
import org.labkey.study.model.CohortManager;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;


/**
 * User: Matthew
 * Date: Jan 12, 2006
 * Time: 1:16:44 PM
 */
public abstract class AbstractDatasetImportTask<FactoryType extends AbstractDatasetImportTaskFactory<FactoryType>> extends PipelineJob.Task<TaskFactory>
{
    transient private StudyManager _studyManager = StudyManager.getInstance();
    transient private CohortManager _cohortManager = CohortManager.getInstance();
    StudyImportContext _ctx;

    public AbstractDatasetImportTask(FactoryType factory, PipelineJob job, StudyImportContext ctx)
    {
        super(factory, job);
        _ctx = ctx;
    }

    public abstract StudyImpl getStudy();
    protected abstract String getDatasetsFileName() throws ImportException;
    protected abstract VirtualFile getDatasetsDirectory() throws ImportException;

    protected StudyManager getStudyManager()
    {
        return _studyManager;
    }

    protected CohortManager getCohortManager()
    {
        return _cohortManager;
    }

    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        try
        {
            PipelineJob job = getJob();
            doImport(getDatasetsDirectory(), getDatasetsFileName(), job, _ctx, getStudy(), true);

            return new RecordedActionSet();
        }
        catch (Exception e)
        {
            throw new PipelineJobException("Exception importing datasets", e);
        }
    }

    public static List<DatasetDefinition> doImport(VirtualFile datasetsDirectory, String datasetsFileName, PipelineJob job,
                                                   StudyImportContext ctx, StudyImpl study, boolean syncParticipantVisit)
    {
        if (!ctx.isDataTypeSelected(StudyImportDatasetTask.getType()))
            return Collections.emptyList();

        if (null == datasetsDirectory || null == datasetsFileName)
            return Collections.emptyList();

        DatasetFileReader reader;
        if (datasetsDirectory.list().contains(datasetsFileName))
        {
            // dataset metadata provided
            reader = new DatasetFileReader(datasetsDirectory, datasetsFileName, study, ctx);
        }
        else
        {
            ctx.getLogger().info("Dataset file \"" + datasetsFileName + "\" not found, inferring columns from the dataset files.");
            reader = new DatasetInferSchemaReader(datasetsDirectory, datasetsFileName, study, ctx);
        }

        return doImport(datasetsDirectory, datasetsFileName, job, ctx, study, reader, syncParticipantVisit);
    }

    public static List<DatasetDefinition> doImport(VirtualFile datasetsDirectory, String datasetsFileName, PipelineJob job,
                                                   StudyImportContext ctx, StudyImpl study, DatasetFileReader reader, boolean syncParticipantVisit)
    {
        try
        {
            // Issue 33088: Reload study with phi column result in exception
            // resumeUpdates() later will cause an error without next two lines, because ComplianceQueryLoggingProfilerListener.queryInvoked() (in compliance module) will get null user/container
            // CONSIDER: removing these if ComplianceQueryLoggingProfilerListener can ignore passed in user/container and get them somewhere else (like QueryLogging)
            QueryService.get().setEnvironment(QueryService.Environment.USER, ctx.getUser());
            QueryService.get().setEnvironment(QueryService.Environment.CONTAINER, study.getContainer());

            QuerySnapshotService.get(StudySchema.getInstance().getSchemaName()).pauseUpdates(study.getContainer());
            List<String> errors = new ArrayList<>();

            try
            {
                reader.validate(errors);

                for (String error : errors)
                    ctx.getLogger().error(error);

                Set<String> notFound = reader.getDatasetsNotFound();
                if (!notFound.isEmpty())
                {
                    ctx.getLogger().warn("Could not find definitions for " + notFound.size() + " dataset data files: " + StringUtils.join(notFound, ", "));
                }
            }
            catch (Exception x)
            {
                ctx.getLogger().error("Parse failed: " + datasetsFileName, x);
                return Collections.emptyList();
            }

            List<DatasetImportRunnable> runnables = reader.getRunnables();
            ctx.getLogger().info("Start batch " + datasetsFileName);

            List<DatasetDefinition> datasets = new ArrayList<>();

            for (DatasetImportRunnable runnable : runnables)
            {
                String validate = runnable.validate();
                if (validate != null)
                {
                    if (job != null)
                        job.setStatus(validate);
                    ctx.getLogger().error(validate);
                    continue;
                }
                String statusMsg = "" + runnable._action + " " + runnable._datasetDefinition.getLabel();
                datasets.add(runnable._datasetDefinition);
                if (runnable.getFileName() != null)
                    statusMsg += " using file " + runnable.getFileName();
                if (job != null)
                    job.setStatus(statusMsg);

                try
                {
                    runnable.run();
                }
                catch (Exception x)
                {
                    ctx.getLogger().error("Unexpected error loading " + runnable.getFileName(), x);
                }
            }

            ctx.getLogger().info("Finish batch " + datasetsFileName);

            if (syncParticipantVisit)
            {
                if (job != null)
                    job.setStatus("UPDATE participants");
                ctx.getLogger().info("Updating participant visits");
                StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(ctx.getUser(), datasets, ctx.getLogger());
                ctx.getLogger().info("Finished updating participants");
            }

            return datasets;
        }
        catch (CancelledException e)
        {
            throw e;
        }
        catch (RuntimeException x)
        {
            ctx.getLogger().error("Unexpected error", x);
            throw x;
        }
        finally
        {
            QuerySnapshotService.get(StudySchema.getInstance().getSchemaName()).resumeUpdates(ctx.getUser(), study.getContainer());
            File lock = StudyPipeline.lockForDataset(study, Path.parse(datasetsDirectory.getLocation()).append(datasetsFileName));
            if (lock.exists() && lock.canRead() && lock.canWrite())
                lock.delete();
        }
    }


    public enum Action
    {
        REPLACE,
        APPEND,
        DELETE,
//            MERGE
    }
}