/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
package org.labkey.experiment.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.FileXarSource;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.pipeline.XarGeneratorFactorySettings;
import org.labkey.api.exp.pipeline.XarGeneratorId;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PropertiesJobSupport;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.FileType;
import org.labkey.api.util.NetworkDrive;
import org.labkey.experiment.DataURLRelativizer;
import org.labkey.experiment.LSIDRelativizer;
import org.labkey.experiment.XarExporter;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates an experiment run to represent the work that the task's job has done so far.
 * User: jeckels
 * Date: Jul 25, 2008
*/
public class XarGeneratorTask extends PipelineJob.Task<XarGeneratorTask.Factory> implements XarWriter
{
    public static class Factory extends AbstractTaskFactory<XarGeneratorFactorySettings, Factory>
    {
        private FileType _outputType = XarGeneratorId.FT_PIPE_XAR_XML;
        private boolean _loadFiles = true;
        private String _statusName = "IMPORT RESULTS";

        public Factory()
        {
            super(XarGeneratorId.class);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new XarGeneratorTask(this, job);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public FileType getOutputType()
        {
            return _outputType;
        }

        public boolean isLoadFiles()
        {
            return _loadFiles;
        }

        @Override
        public String getStatusName()
        {
            return _statusName;
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        protected Path getXarFile(PipelineJob job)
        {
            FileAnalysisJobSupport jobSupport = job.getJobSupport(FileAnalysisJobSupport.class);
            return getOutputType().newFile(jobSupport.getAnalysisDirectoryPath(), jobSupport.getBaseName());
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            // We can use an existing XAR file from disk if it's been generated, but we need to load it because
            // there's no simple way to tell if it's already been imported or not, or if it's been subsequently deleted
            return false;
        }

        @Override
        public void configure(XarGeneratorFactorySettings settings)
        {
            super.configure(settings);

            if (settings.getOutputExt() != null)
                _outputType = new FileType(settings.getOutputExt());
            _loadFiles = settings.isLoadFiles();
            if (!_loadFiles)
                _statusName = "GENERATING EXPERIMENT";
        }
    }
    
    public XarGeneratorTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    /**
     * The basic steps are:
     * 1. Start a transaction.
     * 2. Create a protocol and a run and insert them into the database, not loading the data files.
     * 3. Export the run and protocol to a temporary XAR on the file system.
     * 4. Commit the transaction.
     * 5. Import the temporary XAR (not reloading the runs it references), which causes its referenced data files to load.
     * 6. Rename the temporary XAR to its permanent name.
     *
     * This allows us to quickly tell if the task is already complete by checking for the XAR file. If it exists, we
     * can simply reimport it. If the temporary file exists, we can skip directly to step 5 above. 
     */
    @Override
    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        try
        {
            // Keep track of all of the runs that have been created by this task
            Set<ExpRun> importedRuns = new HashSet<>();
            if (_factory.isLoadFiles())
            {
                Path permanentXAR = _factory.getXarFile(getJob());
                if (NetworkDrive.exists(permanentXAR))
                {
                    // Be sure that it's been imported (and not already deleted from the database)
                    importedRuns.addAll(ExperimentService.get().importXar(new FileXarSource(permanentXAR, getJob()), getJob(), false));
                }
                else
                {
                    if (!NetworkDrive.exists(getLoadingXarFile()))
                    {
                        XarSource source = new XarGeneratorSource(getJob(), _factory.getXarFile(getJob()));
                        importedRuns.add(ExpGeneratorHelper.insertRun(getJob(), source, this));
                    }

                    // Load the data files for this run
                    importedRuns.addAll(ExperimentService.get().importXar(new FileXarSource(getLoadingXarFile(), getJob()), getJob(), false));

                    Files.move(getLoadingXarFile(), permanentXAR);
                }
            }
            else
            {
                importedRuns.add(ExpGeneratorHelper.insertRun(getJob(), null, null));
            }

            // save any job-level custom properties from the run
            if (getJob() instanceof PropertiesJobSupport)
            {
                PropertiesJobSupport jobSupport = getJob().getJobSupport(PropertiesJobSupport.class);
                for (Map.Entry<PropertyDescriptor, Object> prop : jobSupport.getProps().entrySet())
                {
                    for (ExpRun importedRun : importedRuns)
                        importedRun.setProperty(getJob().getUser(), prop.getKey(), prop.getValue());
                }
            }

            // Check if we've been cancelled. If so, delete any newly created runs from the database
            PipelineStatusFile statusFile = PipelineService.get().getStatusFile(getJob().getLogFile());
            if (statusFile != null && (PipelineJob.TaskStatus.cancelled.matches(statusFile.getStatus()) || PipelineJob.TaskStatus.cancelling.matches(statusFile.getStatus())))
            {
                for (ExpRun importedRun : importedRuns)
                {
                    getJob().info("Deleting run " + importedRun.getName() + " due to cancellation request");
                    importedRun.delete(getJob().getUser());
                }
            }
        }
        catch (RuntimeSQLException | ValidationException e)
        {
            throw new PipelineJobException("Failed to save experiment run in the database", e);
        }
        catch (ExperimentException e)
        {
            throw new PipelineJobException("Failed to import data files", e);
        }
        catch (IOException e)
        {
            throw new PipelineJobException("Unable to read data files", e);
        }
        return new RecordedActionSet();
    }

    // XarWriter interface
    @Override
    public void writeToDisk(ExpRun run) throws PipelineJobException
    {
        Path f = getLoadingXarFile();
        Path tempFile = f.getParent().resolve(f.getFileName().toString() + ".temp");

        try
        {
            XarExporter exporter = new XarExporter(LSIDRelativizer.FOLDER_RELATIVE, DataURLRelativizer.RUN_RELATIVE_LOCATION.createURLRewriter(), getJob().getUser());
            exporter.addExperimentRun(run);

            try (OutputStream fOut = new BufferedOutputStream(Files.newOutputStream(tempFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)))
            {
                exporter.dumpXML(fOut);
                fOut.close();
                Files.move(tempFile, f, StandardCopyOption.ATOMIC_MOVE);
            }
        }
        catch (ExperimentException | IOException e)
        {
            throw new PipelineJobException("Failed to write XAR to disk", e);
        }
    }

    private Path getLoadingXarFile()
    {
        Path xarPath = _factory.getXarFile(getJob());
        return xarPath.resolve(xarPath + ".loading");
    }
}
