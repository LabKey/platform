/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.experiment.pipeline.ExperimentPipelineProvider;
import org.labkey.api.exp.ExperimentException;
import org.labkey.experiment.xar.XarExportSelection;

import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

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
    private XarExportSelection _selection;
    private final String _xarXmlFileName;

    public XarExportPipelineJob(ViewBackgroundInfo info, PipeRoot root, String fileName, LSIDRelativizer lsidRelativizer, XarExportSelection selection, String xarXmlFileName) throws SQLException
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
        setStatus(TaskStatus.waiting);
    }

    public ActionURL getStatusHref()
    {
        return null;
    }

    public String getDescription()
    {
        return "XAR export - " + _fileName;
    }

    public void run()
    {
        setStatus("EXPORTING");

        FileOutputStream fOut = null;
        try
        {
            getLogger().info("Starting to write XAR to " + _exportFile.getPath());
            XarExporter exporter = new XarExporter(_lsidRelativizer, _selection, getUser(), _xarXmlFileName, getLogger());
            _exportFile.getParentFile().mkdirs();
            fOut = new FileOutputStream(_exportFile);
            exporter.write(fOut);
            getLogger().info("Export complete");
            setStatus(TaskStatus.complete);
        }
        catch (RuntimeException | IOException | SQLException | ExperimentException e)
        {
            logFailure(e);
        }
        finally
        {
            if (fOut != null) { try { fOut.close(); } catch (IOException ignored) {} }
        }
    }

    private void logFailure(Throwable e)
    {
        getLogger().error("Failed when exporting XAR", e);
    }
}
