/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.data.Container;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.DateUtil;
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.LSIDRelativizer;
import org.labkey.experiment.DataURLRelativizer;
import org.labkey.experiment.XarReader;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.ExpRunImpl;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.fhcrc.cpas.exp.xml.ExperimentArchiveDocument;
import org.fhcrc.cpas.exp.xml.ExperimentArchiveType;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: jeckels
 * Date: Feb 14, 2007
 */
public class MoveRunsPipelineJob extends PipelineJob
{
    private final int[] _runIds;
    private Container _sourceContainer;

    public MoveRunsPipelineJob(ViewBackgroundInfo info, Container sourceContainer, int[] runIds) throws SQLException, IOException
    {
        super(ExperimentPipelineProvider.NAME, info);
        _runIds = runIds;
        _sourceContainer = sourceContainer;
        PipeRoot root = PipelineService.get().findPipelineRoot(info.getContainer());
        if (!NetworkDrive.exists(root.getRootPath()))
        {
            throw new FileNotFoundException("Could not find pipeline root on disk: " + root.getRootPath());
        }
        File moveRunLogsDir = ExperimentPipelineProvider.getMoveDirectory(root);
        moveRunLogsDir.mkdirs();
        File logFile = File.createTempFile("moveRun", ".log", moveRunLogsDir);
        setLogFile(logFile);

        StringBuilder sb = new StringBuilder();
        sb.append(getDescription());
        sb.append("\n");
        for (int runId : _runIds)
        {
            ExpRun run = ExperimentService.get().getExpRun(runId);
            if (run == null)
            {
                sb.append("No run found for RunId ");
                sb.append(runId);
            }
            else
            {
                sb.append(run.getLSID());
                sb.append(", id = ");
                sb.append(runId);
            }
            sb.append("\n");
        }
        getLogger().info(sb.toString());
    }

    public String getDescription()
    {
        return "Move " + _runIds.length + " run" + (_runIds.length == 1 ? "" : "s") + " from " + _sourceContainer.getPath() + " to " + getContainer().getPath();
    }

    public ActionURL getStatusHref()
    {
        return null;
    }

    public void run()
    {
        setStatus("MOVING RUNS");
        try
        {

            for (int runId : _runIds)
            {
                XarExporter exporter = new XarExporter(LSIDRelativizer.PARTIAL_FOLDER_RELATIVE, DataURLRelativizer.ORIGINAL_FILE_LOCATION);
                ExpRunImpl experimentRun = ExperimentServiceImpl.get().getExpRun(runId);
                if (experimentRun != null)
                {
                    exporter.addExperimentRun(experimentRun);

                    ByteArrayOutputStream bOut = new ByteArrayOutputStream();
                    exporter.dumpXML(bOut);

                    Map<String,Integer> dataFiles = new HashMap<String,Integer>();

                    synchronized (ExperimentService.get().getImportLock())
                    {
                        ExperimentServiceImpl.get().getSchema().getScope().beginTransaction();
                        try
                        {
                            for (ExpData oldData : ExperimentServiceImpl.get().getAllDataUsedByRun(runId))
                            {
                                ExperimentDataHandler handler = oldData.findDataHandler();
                                handler.beforeMove(oldData, _sourceContainer, getUser());
                            }

                            ExpData[] datas = ExperimentService.get().deleteExperimentRunForMove(runId, _sourceContainer, getUser());
                            for (ExpData data : datas)
                            {
                                if (data.getDataFileUrl() != null)
                                {
                                    dataFiles.put(data.getDataFileUrl(),data.getRowId());
                                }
                            }

                            MoveRunsXarSource xarSource = new MoveRunsXarSource(bOut.toString(), new File(experimentRun.getFilePathRoot()), this);
                            XarReader reader = new XarReader(xarSource, this);
                            reader.parseAndLoad(false);

                            List<String> runLSIDs = reader.getProcessedRunsLSIDs();
                            assert runLSIDs.size() == 1 : "Expected a single run to be loaded";

                            for (String dataURL : dataFiles.keySet())
                            {
                                ExpData newData = ExperimentService.get().getExpDataByURL(xarSource.getCanonicalDataFileURL(dataURL), getContainer());
                                if (newData != null)
                                {
                                    ExperimentDataHandler handler = newData.findDataHandler();
                                    handler.runMoved(newData, _sourceContainer, getContainer(), experimentRun.getLSID(), runLSIDs.get(0), getUser(), dataFiles.get(dataURL));
                                }
                            }
                            ExperimentServiceImpl.get().getSchema().getScope().commitTransaction();
                        }
                        finally
                        {
                            ExperimentServiceImpl.get().getSchema().getScope().closeConnection();
                        }
                    }
                }
                else
                {
                    getLogger().info("Run with id " + runId + " is no longer available in the system");
                }
            }

            setStatus(PipelineJob.COMPLETE_STATUS);
        }
        catch (Throwable t)
        {
            getLogger().fatal("Exception during move", t);
            getLogger().fatal("Move FAILED");
            if (t instanceof BatchUpdateException)
            {
                getLogger().fatal("Underlying exception", ((BatchUpdateException)t).getNextException());
            }
            setStatus(PipelineJob.ERROR_STATUS, "Move FAILED");
        }
    }

    public static class MoveRunsXarSource extends XarSource
    {
        private static final Logger _log = Logger.getLogger(MoveRunsXarSource.class);

        private final String _xml;
        private File _logFile;
        private File _logFileDir;

        private final String _uploadTime;

        private String _experimentName;
        private File _root;

        public MoveRunsXarSource(String xml, File root, PipelineJob job) throws ExperimentException
        {
            super(job);
            _xml = xml;
            _root = root;

            int retry = 0;
            while (_logFileDir == null)
            {
                try
                {
                    _logFileDir = File.createTempFile("xarupload", "");
                }
                catch (IOException e)
                {
                    if (++retry > 10)
                    {
                        throw new ExperimentException("Unable to create a log file", e);
                    }
                    _log.warn("Failed to create log file, retrying...", e);
                }
            }

            _logFileDir.delete();
            _logFileDir.mkdir();
            _logFileDir.deleteOnExit();
            _logFile = new File(_logFileDir, "upload.xar.log");
            _logFile.deleteOnExit();
            _uploadTime = DateUtil.formatDateTime();
        }

        public ExperimentArchiveDocument getDocument() throws XmlException, IOException
        {
            ExperimentArchiveDocument doc = ExperimentArchiveDocument.Factory.parse(_xml);
            ExperimentArchiveType ea = doc.getExperimentArchive();
            if (ea != null)
            {
                if (ea.getExperimentArray() != null && ea.getExperimentArray().length > 0)
                {
                    _experimentName = ea.getExperimentArray()[0].getName();
                }
            }
            return doc;
        }

        public File getRoot()
        {
            return _root;
        }

        public boolean shouldIgnoreDataFiles()
        {
            return true;
        }

        public String canonicalizeDataFileURL(String dataFileURL) throws XarFormatException
        {
            File f = new File(dataFileURL);
            File dataFile;
            if (!f.isAbsolute())
            {
                dataFile = new File(getRoot(), dataFileURL);
            }
            else
            {
                dataFile = f;
            }
            try
            {
                return dataFile.getCanonicalFile().toURI().toString();
            }
            catch (IOException e)
            {
                throw new XarFormatException(e);
            }
        }

        public File getLogFile() throws IOException
        {
            return _logFile;
        }

        public String toString()
        {
            String result = "Uploaded file: " + _uploadTime;
            if (_experimentName != null)
            {
                result += _experimentName;
            }
            return result;
        }

        public void cleanup()
        {
            if (_logFile != null)
            {
                _logFile.delete();
                _logFile = null;
            }
            if (_logFileDir != null)
            {
                _logFileDir.delete();
                _logFileDir = null;
            }
        }
    }
}
