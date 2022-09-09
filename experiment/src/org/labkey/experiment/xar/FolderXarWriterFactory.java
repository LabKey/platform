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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.XarExportContext;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.writer.VirtualFile;
import org.labkey.experiment.LSIDRelativizer;
import org.labkey.experiment.XarExporter;

import java.io.OutputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    public static class FolderXarWriter extends BaseFolderWriter
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
                if (getProtocols(container).size() > 0 || getRuns(null, container).size() > 0)
                {
                    return true;
                }
            }

            return false;
        }

        private List<ExpRun> getRuns(@Nullable FolderExportContext ctx, Container c)
        {
            XarExportContext xarCtx = null;
            if (ctx != null)
                xarCtx = ctx.getContext(XarExportContext.class);

            // don't include the sample derivation runs, we now have a separate exporter explicitly for sample types
            // if an additional context has been furnished, filter out runs not included in this export
            final XarExportContext fxarCtx = xarCtx;
            List<ExpRun> allRuns = ExperimentService.get().getExpRuns(c, null, null).stream()
                    .filter(
                        run -> !run.getProtocol().getLSID().equals(ExperimentService.SAMPLE_DERIVATION_PROTOCOL_LSID)
                                && !run.getProtocol().getLSID().equals(ExperimentService.SAMPLE_ALIQUOT_PROTOCOL_LSID)
                                && !"recipe".equalsIgnoreCase(run.getProtocol().getImplementationName())
                                && !"recipe".equalsIgnoreCase(run.getProtocol().getLSIDNamespacePrefix())
                            && (fxarCtx == null || fxarCtx.getIncludedAssayRuns().contains(run.getRowId()))
                    )
                    .collect(Collectors.toList());
            // the smJobRuns can make reference to assay designs, so we will put all the SM Task and Protocols at the end to assure
            // the assay definitions have already been processed and can be resolved properly.
            List<ExpRun> reorderedRuns = allRuns.stream()
                    .filter((run -> !ExpProtocol.isSampleWorkflowProtocol(run.getProtocol().getLSID())))
                    .collect(Collectors.toList());
            List<ExpRun> smJobRuns = allRuns.stream()
                    .filter((run -> ExpProtocol.isSampleWorkflowProtocol(run.getProtocol().getLSID())))
                    .collect(Collectors.toList());
            reorderedRuns.addAll(smJobRuns);
            return reorderedRuns;
//            return allRuns;
        }

        private List<Integer> getProtocols(Container c)
        {
            // don't include the sample derivation runs, we now have a separate exporter explicitly for sample types
            List<ExpProtocol> protocols = ExperimentService.get().getExpProtocols(c)
                    .stream()
                    .filter(protocol ->
                            !protocol.getLSID().startsWith(ExperimentService.SAMPLE_DERIVATION_PROTOCOL_NAME) &&
                                    !protocol.getLSID().startsWith(ExperimentService.SAMPLE_ALIQUOT_PROTOCOL_NAME)
                            && !"recipe".equalsIgnoreCase(protocol.getImplementationName())
                            && !"recipe".equalsIgnoreCase(protocol.getLSIDNamespacePrefix())
                    )
                    .collect(Collectors.toList());
            // the sm template tasks can make reference to assay designs, so we will put all the SM Job and Task Protocols at the end to assure
            // the assay definitions have already been processed and can be resolved properly.
            List<ExpProtocol> reorderedProtocols = protocols.stream()
                    .filter((protocol -> !ExpProtocol.isSampleWorkflowProtocol(protocol.getLSID()))
                    )
                    .collect(Collectors.toList());
            protocols.stream()
                    .filter(protocol -> ExpProtocol.isSampleWorkflowTaskProtocol(protocol.getLSID()))
                    .forEach(reorderedProtocols::add);
            return reorderedProtocols.stream()
                    .map(ExpObject::getRowId)
                    .collect(Collectors.toList());
        }

        @Override
        public void write(Container c, FolderExportContext ctx, VirtualFile vf) throws Exception
        {
            XarExportSelection selection = new XarExportSelection();
            ExperimentService expService = ExperimentService.get();

            // Get all the experiments in the container.
            List<? extends ExpExperiment> experiments = expService.getExperiments(c, ctx.getUser(), false, false);

            // Add experiments.
            for(ExpExperiment exp: experiments)
            {
                selection.addExperimentIds(exp.getRowId());
            }

            selection.addProtocolIds(getProtocols(c));

            selection.addRuns(getRuns(ctx, c));

            ctx.getXml().addNewXar().setDir(XAR_DIRECTORY);
            VirtualFile xarDir = vf.getDir(XAR_DIRECTORY);

            XarExporter exporter = new XarExporter(LSIDRelativizer.FOLDER_RELATIVE, selection, ctx.getUser(), XAR_XML_FILE_NAME, ctx.getLogger());

            try (OutputStream fOut = xarDir.getOutputStream(XAR_FILE_NAME))
            {
                exporter.writeAsArchive(fOut);
            }
        }

        @Override
        public boolean includeWithTemplate()
        {
            return false;
        }
    }
}
