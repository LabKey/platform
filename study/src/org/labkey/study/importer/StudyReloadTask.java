/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.study.importer;

import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StudyReloadTask extends PipelineJob.Task<StudyReloadTask.Factory>
{
    public static final String SKIP_QUERY_VALIDATION = "skipQueryValidation";
    public static final String FAIL_FOR_UNDEFINED_VISITS = "failForUndefinedVisits";

    private StudyReloadTask(StudyReloadTask.Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @NotNull
    @Override
    public RecordedActionSet run()
    {
        PipelineJob job = getJob();
        FileAnalysisJobSupport support = job.getJobSupport(FileAnalysisJobSupport.class);
        job.setLogFile(new File(support.getDataDirectory(), FileUtil.makeFileNameWithTimestamp("triggered_study_reload", "log")));
        Map<String, String> params = support.getParameters();
        StudyReload.ReloadTask reloadTask = new StudyReload.ReloadTask();
        String containerId = getJob().getContainer().getId();

        try
        {
            ImportOptions options = new ImportOptions(containerId, job.getUser().getUserId());

            if (params.containsKey(SKIP_QUERY_VALIDATION))
                options.setSkipQueryValidation(BooleanUtils.toBoolean(params.get(SKIP_QUERY_VALIDATION)));
            if (params.containsKey(FAIL_FOR_UNDEFINED_VISITS))
                options.setFailForUndefinedVisits(BooleanUtils.toBoolean(params.get(FAIL_FOR_UNDEFINED_VISITS)));

            options.setAnalysisDir(support.getDataDirectoryPath());
            StudyReload.ReloadStatus status = reloadTask.attemptTriggeredReload(options, "a configured study reload filewatcher");
            job.setStatus(status.getMessage());
        }
        catch (ImportException ie)
        {
            Container c = ContainerManager.getForId(containerId);
            String message = null != c ? "Folder: " + c.getPath() : "";

            getJob().getLogger().error("Study reload failed. " + message, ie);
        }
        catch (Throwable t)
        {
            ExceptionUtil.logExceptionToMothership(null, t);
            getJob().getLogger().error("Study reload failed. " + t.getMessage());
        }

        return new RecordedActionSet();
    }


    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(StudyReloadTask.class);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new StudyReloadTask(this, job);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        @Override
        public String getStatusName()
        {
            return "RELOAD STUDY";
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}

