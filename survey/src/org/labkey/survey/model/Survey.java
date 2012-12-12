package org.labkey.survey.model;

import org.labkey.api.data.Entity;

import java.util.Date;

/**
 * Created with IntelliJ IDEA.
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
    private String _responsePk;

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

    public String getResponsePk()
    {
        return _responsePk;
    }

    public void setResponsePk(String responsePk)
    {
        _responsePk = responsePk;
    }
}
