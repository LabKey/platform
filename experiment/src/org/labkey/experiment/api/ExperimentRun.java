/*
 * Copyright (c) 2005-2014 LabKey Corporation
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

import org.labkey.api.exp.api.ExperimentService;

/**
 * Bean class for the exp.experimentrun table.
 * User: migra
 * Date: Jun 14, 2005
 */
public class ExperimentRun extends IdentifiableEntity
{
    private String protocolLSID;
    private String filePathRoot;
    private String comments;
    private Integer jobId;
    private Integer _replacedByRunId;
    private Integer _batchId;

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

    public Integer getJobId()
    {
        return jobId;
    }

    public void setJobId(Integer jobId)
    {
        this.jobId = jobId;
    }

    public void setName(String name)
    {
        int maxLength = ExperimentService.get().getTinfoExperimentRun().getColumn("Name").getScale();
        if (name != null && name.length() > maxLength)
        {
            name = name.substring(0, maxLength - "...".length()) + "...";
        }
        super.setName(name);
    }

    public Integer getReplacedByRunId()
    {
        return _replacedByRunId;
    }

    public void setReplacedByRunId(Integer replacedByRunId)
    {
        _replacedByRunId = replacedByRunId;
    }

    public Integer getBatchId()
    {
        return _batchId;
    }

    public void setBatchId(Integer batchId)
    {
        _batchId = batchId;
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
        result = 31 * result + getRowId();
        result = 31 * result + (protocolLSID != null ? protocolLSID.hashCode() : 0);
        return result;
    }
}
