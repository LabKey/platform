/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.pipeline;

import org.labkey.api.data.Container;
import org.labkey.api.pipeline.browse.PipelinePathForm;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocol;
import org.labkey.api.security.User;
import org.labkey.api.util.FileType;

import java.io.File;

/**
 * User: tgaluhn
 * Date: 2/1/2017
 *
 * Moved from AnalysisController
 */
public class AnalyzeForm extends PipelinePathForm
{
    public enum Params
    {
        path, taskId, file
    }

    private String taskId = "";
    private String protocolName = "";
    private String protocolDescription = "";
    private String[] fileInputStatus = null;
    private String configureXml;
    private String configureJson;
    private boolean saveProtocol = false;
    private boolean runAnalysis = false;
    private boolean activeJobs = false;
    private Boolean allowNonExistentFiles;
    private Boolean includeWorkbooks = false;
    private boolean allowProtocolRedefinition = false;
    private String pipelineDescription;

    private static final String UNKNOWN_STATUS = "UNKNOWN";

    public AnalyzeForm()
    {}

    public AnalyzeForm(Container container, User user, String taskId, String protocolName)
    {
        setContainer(container);
        setUser(user);
        setTaskId(taskId);
        setProtocolName(protocolName);
    }

    public void initStatus(AbstractFileAnalysisProtocol protocol, File dirData, File dirAnalysis)
    {
        if (fileInputStatus != null)
            return;

        activeJobs = false;

        int len = getFile().length;
        fileInputStatus = new String[len + 1];
        for (int i = 0; i < len; i++)
            fileInputStatus[i] = initStatusFile(protocol, dirData, dirAnalysis, getFile()[i], true);

        // TODO comment why this special status is added at the end (or make this a separate variable)
        fileInputStatus[len] = initStatusFile(protocol, dirData, dirAnalysis, null, false);
    }

    private String initStatusFile(AbstractFileAnalysisProtocol protocol, File dirData, File dirAnalysis,
                                  String fileInputName, boolean statusSingle)
    {
        if (protocol == null)
        {
            return UNKNOWN_STATUS;
        }

        File fileStatus = null;

        if (!statusSingle)
        {
            fileStatus = PipelineJob.FT_LOG.newFile(dirAnalysis,
                    protocol.getJoinedBaseName());
        }
        else if (fileInputName != null)
        {
            File fileInput = new File(dirData, fileInputName);
            FileType ft = protocol.findInputType(fileInput);
            if (ft != null)
                fileStatus = PipelineJob.FT_LOG.newFile(dirAnalysis, ft.getBaseName(fileInput));
        }

        if (fileStatus != null)
        {
            PipelineStatusFile sf = PipelineService.get().getStatusFile(fileStatus);
            if (sf == null)
                return null;

            activeJobs = activeJobs || sf.isActive();
            return sf.getStatus();
        }

        // Failed to get status.  Assume job is active, and return unknown status.
        activeJobs = true;
        return UNKNOWN_STATUS;
    }

    public String getTaskId()
    {
        return taskId;
    }

    public void setTaskId(String taskId)
    {
        this.taskId = taskId;
    }

    public String getConfigureXml()
    {
        return configureXml;
    }

    public void setConfigureXml(String configureXml)
    {
        this.configureXml = (configureXml == null ? "" : configureXml);
    }

    public String getConfigureJson()
    {
        return configureJson;
    }

    public void setConfigureJson(String configureJson)
    {
        this.configureJson = configureJson;
    }

    public String getProtocolName()
    {
        return protocolName;
    }

    public void setProtocolName(String protocolName)
    {
        this.protocolName = (protocolName == null ? "" : protocolName);
    }

    public String getProtocolDescription()
    {
        return protocolDescription;
    }

    public void setProtocolDescription(String protocolDescription)
    {
        this.protocolDescription = (protocolDescription == null ? "" : protocolDescription);
    }

    public String[] getFileInputStatus()
    {
        return fileInputStatus;
    }

    public boolean isActiveJobs()
    {
        return activeJobs;
    }

    public Boolean getIncludeWorkbooks()
    {
        return includeWorkbooks;
    }

    public void setIncludeWorkbooks(Boolean includeWorkbooks)
    {
        this.includeWorkbooks = includeWorkbooks;
    }

    public boolean isSaveProtocol()
    {
        return saveProtocol;
    }

    public void setSaveProtocol(boolean saveProtocol)
    {
        this.saveProtocol = saveProtocol;
    }

    public boolean isRunAnalysis()
    {
        return runAnalysis;
    }

    public void setRunAnalysis(boolean runAnalysis)
    {
        this.runAnalysis = runAnalysis;
    }

    public Boolean isAllowNonExistentFiles()
    {
        return allowNonExistentFiles;
    }

    public void setAllowNonExistentFiles(Boolean allowNonExistentFiles)
    {
        this.allowNonExistentFiles = allowNonExistentFiles;
    }

    public boolean isAllowProtocolRedefinition()
    {
        return allowProtocolRedefinition;
    }

    public void setAllowProtocolRedefinition(boolean allowProtocolRedefinition)
    {
        this.allowProtocolRedefinition = allowProtocolRedefinition;
    }

    public static String getDefaultXMLParameters()
    {
        return "<?xml version=\"1.0\"?><bioml/>";
    }

    public String getPipelineDescription()
    {
        return pipelineDescription;
    }

    public void setPipelineDescription(String pipelineDescription)
    {
        this.pipelineDescription = pipelineDescription;
    }
}
