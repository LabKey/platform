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

import org.labkey.api.exp.pipeline.XarTemplateSubstitutionFactorySettings;
import org.labkey.api.exp.pipeline.XarTemplateSubstitutionId;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.FileType;
import org.labkey.api.util.NetworkDrive;

import java.io.*;
import java.sql.SQLException;
import java.util.Map;
import java.util.List;
import java.util.Collections;

/**
 * <code>XarGeneratorTask</code>
 */
public class XarTemplateSubstitutionTask extends PipelineJob.Task<XarTemplateSubstitutionTask.Factory> implements XarTemplateSubstitutionId
{
    public static class Factory extends AbstractTaskFactory<XarTemplateSubstitutionFactorySettings, Factory> implements XarTemplateSubstitutionId.Factory
    {
        private FileType _inputType;
        private FileType _outputType = XarTemplateSubstitutionId.FT_PIPE_XAR_XML;

        public Factory()
        {
            super(XarTemplateSubstitutionId.class);
        }

        protected void configure(XarTemplateSubstitutionFactorySettings settings)
        {
            super.configure(settings);

            if (settings.getOutputExt() != null)
                _outputType = new FileType(settings.getOutputExt());

            if (settings.getInputExt() != null)
                _inputType = new FileType(settings.getInputExt());
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new XarTemplateSubstitutionTask(this, job);
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

        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        public boolean isJobComplete(PipelineJob job) throws IOException, SQLException
        {
            FileAnalysisJobSupport support = job.getJobSupport(FileAnalysisJobSupport.class);
            String baseName = support.getBaseName();
            File dirAnalysis = support.getAnalysisDirectory();

            return NetworkDrive.exists(_outputType.newFile(dirAnalysis, baseName));
        }
    }

    protected XarTemplateSubstitutionTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public FileAnalysisJobSupport getJobSupport()
    {
        return getJob().getJobSupport(FileAnalysisJobSupport.class);
    }

    public List<RecordedAction> run() throws PipelineJobException
    {
        try
        {
            WorkDirFactory factory = PipelineJobService.get().getWorkDirFactory();
            WorkDirectory wd = factory.createWorkDirectory(getJob().getJobGUID(), getJobSupport(), getJob().getLogger());

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
            throw new PipelineJobException(e);
        }
        return Collections.emptyList();
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
