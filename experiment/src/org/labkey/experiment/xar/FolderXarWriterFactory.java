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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
    private static List<Writer> CHILD_WRITERS = Arrays.asList(new ExperimentRunsWriter());

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
                if (!getProtocols(container).isEmpty() || hasRuns(container))
                {
                    return true;
                }
            }

            return false;
        }

        @Override
        public @NotNull Collection<Writer<?, ?>> getChildren(boolean sort, boolean forTemplate)
        {
            List<Writer<?,?>> children = new ArrayList<>();

            for (Writer writer : CHILD_WRITERS)
            {
                if (!forTemplate || (forTemplate && writer.includeWithTemplate()))
                    children.add(writer);
            }
            return children;
        }

        private boolean hasRuns(Container c)
        {
            return ExperimentService.get().hasExpRuns(c, this::runFilter);
        }

        private boolean runFilter(ExpRun run) {
            return !run.getProtocol().getLSID().equals(ExperimentService.SAMPLE_DERIVATION_PROTOCOL_LSID)
                    && !run.getProtocol().getLSID().equals(ExperimentService.SAMPLE_ALIQUOT_PROTOCOL_LSID)
                    && !"recipe".equalsIgnoreCase(run.getProtocol().getImplementationName())
                    && !"recipe".equalsIgnoreCase(run.getProtocol().getLSIDNamespacePrefix());
        }

        private List<ExpRun> getRuns(Container c)
        {
            // Don't include the sample derivation runs; we now have a separate exporter explicitly for sample types.
            // Also don't include recipe protocols; there's a separate folder writer and importer for the recipe module.
            // if an additional context has been furnished, filter out runs not included in this export

            List<? extends ExpRun> allRuns = ExperimentServiceImpl.get().getExpRuns(c, null, null, this::runFilter);
            // the smJobRuns can make reference to assay designs, so we will put all the SM Task and Protocols at the end to assure
            // the assay definitions have already been processed and can be resolved properly.
            List<ExpRun> reorderedRuns = allRuns.stream()
                    .filter((run -> !ExpProtocol.isSampleWorkflowProtocol(run.getProtocol().getLSID())))
                    .collect(Collectors.toList());
            List<? extends ExpRun> smJobRuns = allRuns.stream()
                    .filter((run -> ExpProtocol.isSampleWorkflowProtocol(run.getProtocol().getLSID())))
                    .toList();

            reorderedRuns.addAll(smJobRuns);
            return reorderedRuns;
        }

        private List<Integer> getProtocols(Container c)
        {
            // Don't include the sample derivation runs; we now have a separate exporter explicitly for sample types.
            // Also don't include recipe protocols; there's a separate folder writer and importer for the recipe module.
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
                    .filter(protocol -> ExpProtocol.isSampleWorkflowProtocol(protocol.getLSID()))
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

            if (ctx.getDataTypes().contains(FolderArchiveDataTypes.EXPERIMENT_RUNS))
                selection.addRuns(getRuns(c));

            ctx.getXml().addNewXar().setDir(XAR_DIRECTORY);
            VirtualFile xarDir = vf.getDir(XAR_DIRECTORY);

            XarExporter exporter = new XarExporter(ctx.getRelativizedLSIDs(), selection, ctx.getUser(), XAR_XML_FILE_NAME, ctx.getLogger());

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

    public static class ExperimentRunsWriter implements Writer<Container, FolderExportContext>
    {
        @Override
        public @Nullable String getDataType()
        {
            return FolderArchiveDataTypes.EXPERIMENT_RUNS;
        }

        @Override
        public void write(Container object, FolderExportContext ctx, VirtualFile vf) throws Exception
        {
            // noop, serialization occurs in the parent writer and checks the context data types to determine
            // if the assay runs are serialized.
        }

        @Override
        public boolean selectedByDefault(AbstractFolderContext.ExportType type, boolean forTemplate)
        {
            return AbstractFolderContext.ExportType.STUDY != type;
        }
    }
}
