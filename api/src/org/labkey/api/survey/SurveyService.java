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
        /*
         * Returns a map of survey design IDs to the survey design label
         */
        Map<Integer, String> getSurveyDesignMap(Container container);

        Survey[] getSurveys(Container container, User user);
        Survey getSurvey(Container container, User user, int surveyId);

        SurveyDesign[] getSurveyDesigns(Container container, User user);
        SurveyDesign getSurveyDesign(Container container, User user, int surveyDesignId);
    }
}
