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

import org.labkey.api.exp.pipeline.XarGeneratorFactorySettings;
import org.labkey.api.exp.pipeline.XarGeneratorId;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.FileType;
import org.labkey.api.util.NetworkDrive;

import java.io.*;
import java.sql.SQLException;
import java.util.Map;

/**
 * <code>XarGeneratorTask</code>
 */
public class XarGeneratorTask extends PipelineJob.Task implements XarGeneratorId
{
    public static class Factory extends AbstractTaskFactory implements XarGeneratorId.Factory
    {
        private FileType _inputType;
        private FileType _outputType = XarGeneratorId.FT_PIPE_XAR_XML;

        public Factory()
        {
            super(XarGeneratorId.class);
        }

        public TaskFactory cloneAndConfigure(TaskFactorySettings settings) throws CloneNotSupportedException
        {
            Factory factory = (Factory) super.cloneAndConfigure(settings);

            return factory.configure((XarGeneratorFactorySettings) settings);
        }

        private TaskFactory configure(XarGeneratorFactorySettings settings)
        {
            if (settings.getOutputExt() != null)
                _outputType = new FileType(settings.getOutputExt());

            if (settings.getInputExt() != null)
                _inputType = new FileType(settings.getInputExt());

            return this;
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new XarGeneratorTask(job, this);
        }

        public FileType[] getInputTypes()
        {
            return new FileType[] { _inputType };
        }

        public FileType getOutputType()
        {
            return _outputType;
        }

        public String getStatusName()
        {
            return "SAVE EXPERIMENT";
        }

        public boolean isJobComplete(PipelineJob job) throws IOException, SQLException
        {
            FileAnalysisJobSupport support = job.getJobSupport(FileAnalysisJobSupport.class);
            String baseName = support.getBaseName();
            File dirAnalysis = support.getAnalysisDirectory();

            return NetworkDrive.exists(_outputType.newFile(dirAnalysis, baseName));
        }
    }

    Factory _factory;

    protected XarGeneratorTask(PipelineJob job, Factory factory)
    {
        super(job);

        _factory = factory;
    }

    public FileAnalysisJobSupport getJobSupport()
    {
        return getJob().getJobSupport(FileAnalysisJobSupport.class);
    }

    public void run()
    {
        try
        {
            WorkDirFactory factory = PipelineJobService.get().getWorkDirFactory();
            WorkDirectory wd = factory.createWorkDirectory(getJob().getJobGUID(), getJobSupport());

            File fileExperimentXML = wd.newFile(_factory.getOutputType());

            InputStream in = getClass().getClassLoader().getResourceAsStream(getJobSupport().getXarTemplateResource());
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder sb = new StringBuilder();
            String line;
            try
            {
                while ((line = reader.readLine()) != null)
                {
                    sb.append(line);
                    sb.append("\n");
                }
            }
            finally
            {
                reader.close();
            }

            Map<String, String> replaceMap = getJobSupport().getXarTemplateReplacements();
            for (Map.Entry<String, String> entry : replaceMap.entrySet())
                replaceString(sb, entry.getKey(), entry.getValue());

            FileOutputStream fOut = new FileOutputStream(fileExperimentXML);
            PrintWriter writer = new PrintWriter(fOut);
            try
            {
                writer.write(sb.toString());
            }
            finally
            {
                writer.close();
            }

            wd.outputFile(fileExperimentXML);
            wd.remove();
        }
        catch (IOException e)
        {
            getJob().error(e.getMessage(), e);
        }
    }

    protected void replaceString(StringBuilder sb, String oldString, String newString)
    {
        oldString = "@@" + oldString + "@@";
        int index = sb.indexOf(oldString);
        while (index != -1)
        {
            sb.replace(index, index + oldString.length(), newString);
            index = sb.indexOf(oldString);
        }
    }
}
