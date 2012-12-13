package org.labkey.survey.query;

import org.labkey.api.query.QuerySettings;

/**
 * Created with IntelliJ IDEA.
 * User: cnathe
 * Date: 12/13/12
 */
public class SurveyQuerySettings extends QuerySettings
{
    private Integer _surveyDesignId;

    public SurveyQuerySettings(String dataRegionName)
    {
        super(dataRegionName);
    }

    public Integer getSurveyDesignId()
    {
        return _surveyDesignId;
    }

    public void setSurveyDesignId(Integer surveyDesignId)
    {
        _surveyDesignId = surveyDesignId;
    }
}
