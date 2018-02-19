/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.LSIDRelativizer;
import org.labkey.experiment.DataURLRelativizer;
import org.labkey.experiment.XarReader;
import org.labkey.experiment.api.ExpRunImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.fhcrc.cpas.exp.xml.ExperimentArchiveDocument;
import org.fhcrc.cpas.exp.xml.ExperimentArchiveType;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.BatchUpdateException;
import java.sql.SQLException;

/**
 * User: jeckels
 * Date: Sep 8, 2008
 */
public class MoveRunsTask extends PipelineJob.Task<MoveRunsTaskFactory>
{
    public MoveRunsTask(MoveRunsTaskFactory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        MoveRunsPipelineJob job = (MoveRunsPipelineJob)getJob();

        try
        {
            for (int runId : job.getRunIds())
            {
                ExpRunImpl experimentRun = ExperimentServiceImpl.get().getExpRun(runId);
                if (experimentRun != null)
                {
                    moveRun(job, experimentRun);
                }
                else
                {
                    job.info("Run with id " + runId + " is no longer available in the system");
                }
            }
        }
        catch (Throwable t)
        {
            job.error("Exception during move", t);
            job.error("Move FAILED");
            if (t instanceof BatchUpdateException)
            {
                job.error("Underlying exception", ((BatchUpdateException)t).getNextException());
            }
        }
        return new RecordedActionSet();
    }

    private void moveRun(MoveRunsPipelineJob job, ExpRunImpl experimentRun) throws SQLException, ExperimentException, IOException
    {
        XarExporter exporter = new XarExporter(LSIDRelativizer.PARTIAL_FOLDER_RELATIVE, DataURLRelativizer.ORIGINAL_FILE_LOCATION.createURLRewriter(), getJob().getUser());
        exporter.addExperimentRun(experimentRun);

        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        exporter.dumpXML(bOut);

        try (DbScope.Transaction transaction = ExperimentService.get().getSchema().getScope().ensureTransaction())
        {
            Map<String,Integer> dataFiles = new HashMap<>();

            for (ExpData oldData : experimentRun.getAllDataUsedByRun())
            {
                ExperimentDataHandler handler = oldData.findDataHandler();
                handler.beforeMove(oldData, experimentRun.getContainer(), job.getUser());
            }

            for (ExpData data : ExperimentService.get().deleteExperimentRunForMove(experimentRun.getRowId(), job.getUser()))
            {
                if (data.getDataFileUrl() != null)
                {
                    dataFiles.put(data.getDataFileUrl(), data.getRowId());
                }
            }

            MoveRunsXarSource xarSource = new MoveRunsXarSource(bOut.toString(), experimentRun.getFilePathRootPath(), job);
            XarReader reader = new XarReader(xarSource, job);
            reader.parseAndLoad(false);

            List<String> runLSIDs = reader.getProcessedRunsLSIDs();
            assert runLSIDs.size() == 1 : "Expected a single run to be loaded";

            for (String dataURL : dataFiles.keySet())
            {
                ExpData newData = ExperimentService.get().getExpDataByURL(xarSource.getCanonicalDataFileURL(dataURL), job.getSourceContainer());
                if (newData != null)
                {
                    ExperimentDataHandler handler = newData.findDataHandler();
                    handler.runMoved(newData, experimentRun.getContainer(), job.getContainer(), experimentRun.getLSID(), runLSIDs.get(0), job.getUser(), dataFiles.get(dataURL));
                }
            }
            transaction.commit();
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
        private String _root;
        private Container _sourceContainer;

        public MoveRunsXarSource(String xml, Path root, MoveRunsPipelineJob job) throws ExperimentException
        {
            super(job);
            _xml = xml;
            _root = FileUtil.getAbsolutePath(job.getSourceContainer(), root);
            _sourceContainer = job.getSourceContainer();

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
            _uploadTime = DateUtil.formatDateTime(job.getContainer());
        }

        public ExperimentArchiveDocument getDocument() throws XmlException, IOException
        {
            ExperimentArchiveDocument doc = ExperimentArchiveDocument.Factory.parse(_xml, XmlBeansUtil.getDefaultParseOptions());
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
            if (FileUtil.hasCloudScheme(_root))
                throw new RuntimeException("Root is in cloud.");
            return getRootPath().toFile();
        }

        public Path getRootPath()
        {
            return FileUtil.stringToPath(_sourceContainer, _root);
        }

        public boolean shouldIgnoreDataFiles()
        {
            return true;
        }

        public String canonicalizeDataFileURL(String dataFileURL) throws XarFormatException
        {
            URI uri = FileUtil.createUri(dataFileURL);
            Path dataFilePath;
            if (!uri.isAbsolute())
            {
                dataFilePath = getRootPath().resolve(dataFileURL);
            }
            else
            {
                dataFilePath = FileUtil.getPath(_sourceContainer, uri);
            }
            return FileUtil.getAbsoluteCaseSensitivePathString(_sourceContainer, dataFilePath.toUri());
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