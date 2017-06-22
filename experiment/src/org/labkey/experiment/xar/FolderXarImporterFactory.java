/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.experiment.xar;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.data.Container;
import org.labkey.api.exp.CompressedXarSource;
import org.labkey.api.exp.XarSource;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.writer.VirtualFile;
import org.labkey.experiment.XarReader;
import org.labkey.experiment.pipeline.ExperimentPipelineJob;
import org.labkey.folder.xml.FolderDocument;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * User: vsharma
 * Date: 6/4/14
 * Time: 9:52 AM
 */
public class FolderXarImporterFactory extends AbstractFolderImportFactory
{
    @Override
    public FolderImporter create()
    {
        return new FolderXarImporter();
    }

    public class FolderXarImporter implements  FolderImporter<FolderDocument.Folder>
    {
        @Override
        public String getDataType()
        {
            return FolderArchiveDataTypes.EXPERIMENTS_AND_RUNS;
        }

        @Override
        public String getDescription()
        {
            return "xar";
        }

        @Override
        public void process(PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            if (!isValidForImportArchive(ctx))
            {
                ctx.getLogger().info("xar directory not found in folder " + ctx.getContainer().getPath());
                return;
            }

            VirtualFile xarDir = ctx.getDir(FolderXarWriterFactory.XAR_DIRECTORY);

            if (job != null)
                job.setStatus("IMPORT " + getDescription());
            ctx.getLogger().info("Loading " + getDescription());

            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(ctx.getContainer());
            if (pipeRoot == null)
            {
                throw new NotFoundException("PipelineRoot not found for container " + ctx.getContainer().getPath());
            }

            final FolderExportXarSourceWrapper xarSourceWrapper = new FolderExportXarSourceWrapper(xarDir, ctx);
            try
            {
                xarSourceWrapper.init();
            }
            catch (Exception e)
            {
                ctx.getLogger().info("Failed to initialize xar source.", e);
                throw e;
            }

            File xarFile = xarSourceWrapper.getXarFile();
            if (xarFile == null)
            {
                ctx.getLogger().error("Could not find a xar file in the xar directory.");
                throw new NotFoundException("Could not find a xar file in the xar directory.");
            }

            if (job == null)
            {
                // Create a new job, if we were not given one.  This will happen if we are creating a new folder
                // from a template folder.
                ViewBackgroundInfo bgInfo = new ViewBackgroundInfo(ctx.getContainer(), ctx.getUser(), null);

                // This will create a new job in the folder.
                // If subfolders are being imported, a job will be created in each subfolder that has a xar file.
                // TODO: Is there a way to create a single job that will import all the files to
                //       their respective folders?
                job = new ExperimentPipelineJob(bgInfo, xarFile, "Xar import", false, pipeRoot)
                {
                    @Override
                    protected XarSource createXarSource(File file)
                    {
                        // Assume this is a .xar or a .zip file
                        return xarSourceWrapper.getXarSource(this);
                    }
                };
                PipelineService.get().queueJob(job);
            }
            else
            {
                XarSource xarSource = xarSourceWrapper.getXarSource(job);
                try
                {
                    xarSource.init();
                }
                catch (Exception e)
                {
                    ctx.getLogger().error("Failed to initialize XAR source", e);
                    throw(e);
                }

                FolderExportXarReader reader = new FolderExportXarReader(xarSource, job);
                reader.parseAndLoad(false);
            }

            ctx.getLogger().info("Done importing " + getDescription());
        }

        @NotNull
        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            return Collections.emptyList();
        }

        @Override
        public boolean isValidForImportArchive(ImportContext<FolderDocument.Folder> ctx) throws ImportException
        {
            return ctx.getDir(FolderXarWriterFactory.XAR_DIRECTORY) != null;
        }
    }

    private class FolderExportXarSourceWrapper
    {
        private final  VirtualFile _xarDir;
        private final ImportContext _importContext;
        private File _xarFile;
        private CompressedXarSource _xarSource;

        public FolderExportXarSourceWrapper(VirtualFile xarDir, ImportContext ctx)
        {
             _xarDir = xarDir;
            _importContext = ctx;
        }

        public void init() throws IOException, ImportException
        {
            if (_xarDir == null)
            {
                throw new IllegalStateException("Xar directory is null");
            }

            for (String file: _xarDir.list())
            {
                if (file.toLowerCase().endsWith(".xar"))
                {
                    _xarFile = new File(_xarDir.getLocation(), file);
                    break;
                }
            }
        }

        public File getXarFile()
        {
            return _xarFile;
        }

        public CompressedXarSource getXarSource(PipelineJob job)
        {
            if (_xarSource == null)
            {
                _xarSource =  new CompressedXarSource(
                        getXarFile(),
                        job,
                        // Initialize the XarSource with the container from the ImportContext instead of the job
                        // so that a XarContext with the correct folder gets created, and runs imported to subfolders
                        // get assigned to the subfolder instead of the parent container.
                        // If were were given a non-null job in FolderXarImporter.process(), job.getContainer() will
                        // return the parent container.
                        _importContext.getContainer())
                ;
            }
            return _xarSource;
        }
    }

    private class FolderExportXarReader extends XarReader
    {
        public FolderExportXarReader(XarSource source, PipelineJob job)
        {
            super(source, job);
        }

        protected Container getContainer()
        {
            // XarReader.getContainer() returns job.getContainer().
            // We want to return the container from the XarContext instead.
            return _xarSource.getXarContext().getContainer();
        }
    }
}
