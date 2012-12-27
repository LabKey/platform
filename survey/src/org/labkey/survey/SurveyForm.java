/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.survey;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.ReturnURLString;

/**
 * Created with IntelliJ IDEA.
 * User: cnathe
 * Date: 12/11/12
  */
public class SurveyForm
{
    protected Integer _rowId;
    protected Integer _surveyDesignId;
    protected String _label;
    protected String _status;
    protected String _responsesPk;
    protected boolean _isSubmitted;
    private ReturnURLString _srcURL;

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(@Nullable Integer rowId)
    {
        _rowId = rowId;
    }

    public Integer getSurveyDesignId()
    {
        return _surveyDesignId;
    }

    public void setSurveyDesignId(Integer surveyDesignId)
    {
        _surveyDesignId = surveyDesignId;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public String getStatus()
    {
        return _status;
    }

    public void setStatus(String status)
    {
        _status = status;
    }

    public String getResponsesPk()
    {
        return _responsesPk;
    }

    public void setResponsesPk(String responsesPk)
    {
        _responsesPk = responsesPk;
    }

    public boolean isSubmitted()
    {
        return _isSubmitted;
    }

    public void setSubmitted(boolean isSubmitted)
    {
        _isSubmitted = isSubmitted;
    }

    public ReturnURLString getSrcURL()
    {
        return _srcURL;
    }

    public void setSrcURL(ReturnURLString srcURL)
    {
        _srcURL = srcURL;
    }
}
