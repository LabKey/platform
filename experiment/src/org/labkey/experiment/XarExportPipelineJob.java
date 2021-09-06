/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.experiment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.experiment.pipeline.ExperimentPipelineProvider;
import org.labkey.experiment.xar.XarExportSelection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Runs in the background and creates a XAR export to the server's file system.
 * User: jeckels
 * Date: Sep 12, 2006
 */
public class XarExportPipelineJob extends PipelineJob
{
    private final File _exportFile;
    private final String _fileName;
    private final LSIDRelativizer _lsidRelativizer;
    private final XarExportSelection _selection;
    private final String _xarXmlFileName;

    @JsonCreator
    protected XarExportPipelineJob(@JsonProperty("_exportFile") File exportFile, @JsonProperty("_fileName") String fileName,
                                   @JsonProperty("_lsidRelativizer") LSIDRelativizer lsidRelativizer, @JsonProperty("_selection") XarExportSelection selection,
                                   @JsonProperty("_xarXmlFileName") String xarXmlFileName)
    {
        super();
        _exportFile = exportFile;
        _fileName = fileName;
        _lsidRelativizer = lsidRelativizer;
        _selection = selection;
        _xarXmlFileName = xarXmlFileName;
    }

    public XarExportPipelineJob(ViewBackgroundInfo info, PipeRoot root, String fileName, LSIDRelativizer lsidRelativizer, XarExportSelection selection, String xarXmlFileName)
    {
        super(ExperimentPipelineProvider.NAME, info, root);
        _fileName = fileName;
        _lsidRelativizer = lsidRelativizer;
        _xarXmlFileName = xarXmlFileName;
        _selection = selection;

        File exportedXarsDir = root.resolvePath("exportedXars");
        exportedXarsDir.mkdir();

        _exportFile = new File(exportedXarsDir, _fileName);

        setLogFile(new File(_exportFile.getPath() + ".log"));

        header("Experiment export to " + _exportFile.getName());
    }

    @Override
    public ActionURL getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "XAR export - " + _fileName;
    }

    @Override
    public void run()
    {
        setStatus("EXPORTING");

        try
        {
            getLogger().info("Starting to write XAR to " + _exportFile.getPath());
            XarExporter exporter = new XarExporter(_lsidRelativizer, _selection, getUser(), _xarXmlFileName, getLogger());
            _exportFile.getParentFile().mkdirs();
            try (FileOutputStream fOut = new FileOutputStream(_exportFile);
                 ViewContext.StackResetter ignored = ViewContext.pushMockViewContext(getUser(), getContainer(), getInfo().getURL()))
            {
                exporter.writeAsArchive(fOut);
            }
            getLogger().info("Export complete");
            setStatus(TaskStatus.complete);
        }
        catch (RuntimeException | IOException | ExperimentException e)
        {
            error("Failed when exporting XAR", e);
            setStatus(TaskStatus.error);
        }
    }
}
