package org.labkey.api.survey;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.survey.model.Survey;
import org.labkey.api.survey.model.SurveyDesign;

import java.util.Map;

/**
 * User: cnathe
 * Date: 1/11/13
 */
public class SurveyService
{
    private static Interface instance;

    public static Interface get()
    {
        return instance;
    }

    public static void setInstance(Interface i)
    {
        instance = i;
    }

    public interface Interface
    {
        Survey[] getSurveys(Container container, User user);
        Survey getSurvey(Container container, User user, int surveyId);
        Survey saveSurvey(Container container, User user, Survey survey);

        SurveyDesign[] getSurveyDesigns(Container container, User user);
        SurveyDesign getSurveyDesign(Container container, User user, int surveyDesignId);

        SurveyDesign saveSurveyDesign(Container container, User user, SurveyDesign surveyDesign);
        void deleteSurveyDesign(Container c, User user, int surveyDesignId, boolean deleteSurveyInstances);
    }
}
