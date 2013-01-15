package org.labkey.survey.model;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.survey.SurveyService;
import org.labkey.api.survey.model.Survey;
import org.labkey.api.survey.model.SurveyDesign;
import org.labkey.survey.SurveyManager;

import java.util.HashMap;
import java.util.Map;

/**
 * User: cnathe
 * Date: 1/11/13
 */
public class SurveyServiceImpl implements SurveyService.Interface
{
    @Override
    public Map<Integer, String> getSurveyDesignMap(Container container)
    {
        Map<Integer, String> map = new HashMap<Integer, String>();
        // TODO: add a CurrentAndProjectAndShared container filter
        SurveyDesign[] surveyDesigns = SurveyManager.get().getSurveyDesigns(container);
        for (SurveyDesign design : surveyDesigns)
            map.put(design.getRowId(), design.getLabel());

        return map;
    }

    @Override
    public Survey[] getSurveys(Container container, User user)
    {
        return SurveyManager.get().getSurveys(container);
    }

    @Override
    public Survey getSurvey(Container container, User user, int surveyId)
    {
        return SurveyManager.get().getSurvey(container, user, surveyId);
    }

    @Override
    public SurveyDesign[] getSurveyDesigns(Container container, User user)
    {
        return SurveyManager.get().getSurveyDesigns(container);
    }

    @Override
    public SurveyDesign getSurveyDesign(Container container, User user, int surveyDesignId)
    {
        return SurveyManager.get().getSurveyDesign(container, user, surveyDesignId);
    }
}
