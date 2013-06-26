/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.api.survey.model;

import org.labkey.api.data.Entity;

import java.util.Date;

/**
 * User: cnathe
 * Date: 12/12/12
 */
public class Survey extends Entity
{
    private int _rowId;
    private String _label;
    private Integer _submittedBy;
    private Date _submitted;
    private String _status;
    private int _surveyDesignId;
    private String _responsesPk;

    public boolean isNew()
    {
        return _rowId == 0;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public Integer getSubmittedBy()
    {
        return _submittedBy;
    }

    public void setSubmittedBy(Integer submittedBy)
    {
        _submittedBy = submittedBy;
    }

    public Date getSubmitted()
    {
        return _submitted;
    }

    public void setSubmitted(Date submitted)
    {
        _submitted = submitted;
    }

    public String getStatus()
    {
        return _status;
    }

    public void setStatus(String status)
    {
        _status = status;
    }

    public int getSurveyDesignId()
    {
        return _surveyDesignId;
    }

    public void setSurveyDesignId(int surveyDesignId)
    {
        _surveyDesignId = surveyDesignId;
    }

    public String getResponsesPk()
    {
        return _responsesPk;
    }

    public void setResponsesPk(String responsesPk)
    {
        _responsesPk = responsesPk;
    }
}
