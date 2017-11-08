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

import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.writer.VirtualFile;
import org.labkey.experiment.LSIDRelativizer;
import org.labkey.experiment.XarExporter;
import org.labkey.folder.xml.FolderDocument;

import java.io.OutputStream;
import java.util.List;
import java.util.Set;

/**
 * User: vsharma
 * Date: 6/4/14
 * Time: 10:05 AM
 */
public class FolderXarWriterFactory implements FolderWriterFactory
{
    public static final String XAR_DIRECTORY = "xar";
    private static final String XAR_FILE_NAME = "experiments_and_runs.xar";
    private static final String XAR_XML_FILE_NAME = XAR_FILE_NAME + ".xml";

    @Override
    public FolderWriter create()
    {
        return new FolderXarWriter();
    }

    public class FolderXarWriter extends BaseFolderWriter
    {
        @Override
        public String getDataType()
        {
            return FolderArchiveDataTypes.EXPERIMENTS_AND_RUNS;
        }

        @Override
        public boolean show(Container c)
        {
            // Show the Xar export option if this container or one of its children has experiment runs.
            Set<Container> containers = ContainerManager.getAllChildren(c);
            for(Container container: containers)
            {
                if(ExperimentService.get().getExpRuns(container, null, null).size() > 0)
                {
                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean selectedByDefault(AbstractFolderContext.ExportType type)
        {
            return false; // Should be unchecked by default.
        }

        @Override
        public void write(Container c, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
        {
            XarExportSelection selection = new XarExportSelection();
            ExperimentService expService = ExperimentService.get();


            // Get all the experiments in the container.
            List<? extends ExpExperiment> experiments = expService.getExperiments(ctx.getContainer(), ctx.getUser(), false, false);

            // Add experiments.
            for(ExpExperiment exp: experiments)
            {
                selection.addExperimentIds(exp.getRowId());
            }

            List<? extends ExpRun> expRuns = ExperimentService.get().getExpRuns(ctx.getContainer(), null, null);
            // Add runs.
            for (ExpRun run: expRuns)
            {
                selection.addRunId(run.getRowId());
            }

            ctx.getXml().addNewXar().setDir(XAR_DIRECTORY);
            VirtualFile xarDir = vf.getDir(XAR_DIRECTORY);

            XarExporter exporter = new XarExporter(LSIDRelativizer.FOLDER_RELATIVE, selection, ctx.getUser(), XAR_XML_FILE_NAME, ctx.getLogger());

            try (OutputStream fOut = xarDir.getOutputStream(XAR_FILE_NAME))
            {
                exporter.write(fOut);
            }
        }

        @Override
        public boolean includeWithTemplate()
        {
            return false;
        }
    }
}
