/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
package org.labkey.experiment.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

/**
 * Bean class for the exp.experimentrun table.
 */
public class ExperimentRun extends IdentifiableEntity
{
    private String protocolLSID;
    private String filePathRoot;
    private String comments;
    private Long jobId;
    private Long _replacedByRunId;
    private Long _batchId;
    private Long _workflowTask;

    public String getProtocolLSID()
    {
        return protocolLSID;
    }

    public void setProtocolLSID(String protocolLSID)
    {
        this.protocolLSID = protocolLSID;
    }

    public String getFilePathRoot()
    {
        return filePathRoot;
    }

    public void setFilePathRoot(String filePathRoot)
    {
        this.filePathRoot = filePathRoot;
    }

    public String getComments()
    {
        return comments;
    }

    public void setComments(String comments)
    {
        this.comments = comments;
    }

    public Long getJobId()
    {
        return jobId;
    }

    public void setJobId(Long jobId)
    {
        this.jobId = jobId;
    }

    @Override
    public void setName(String name)
    {
        int maxLength = ExperimentService.get().getTinfoExperimentRun().getColumn("Name").getScale();
        if (name != null && name.length() > maxLength)
        {
            name = name.substring(0, maxLength - "...".length()) + "...";
        }
        super.setName(name);
    }

    public Long getReplacedByRunId()
    {
        return _replacedByRunId;
    }

    public void setReplacedByRunId(Long replacedByRunId)
    {
        _replacedByRunId = replacedByRunId;
    }

    public Long getBatchId()
    {
        return _batchId;
    }

    public void setBatchId(Long batchId)
    {
        _batchId = batchId;
    }

    public Long getWorkflowTask()
    {
        return _workflowTask;
    }

    public void setWorkflowTask(Long workflowTaskId)
    {
        _workflowTask = workflowTaskId;
    }

    @Override
    public @Nullable ActionURL detailsURL()
    {
        return ExperimentController.getRunGraphURL(getContainer(), getRowId());
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ExperimentRun that = (ExperimentRun) o;

        if (getRowId() != that.getRowId()) return false;
        if (protocolLSID != null ? !protocolLSID.equals(that.protocolLSID) : that.protocolLSID != null) return false;

        return true;
    }

    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + (int)getRowId();
        result = 31 * result + (protocolLSID != null ? protocolLSID.hashCode() : 0);
        return result;
    }

    @Override
    public @Nullable ExpRunImpl getExpObject()
    {
        return new ExpRunImpl(this);
    }
}
