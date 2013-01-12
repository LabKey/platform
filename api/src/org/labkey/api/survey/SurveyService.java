package org.labkey.api.survey;

import org.labkey.api.data.Container;

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
    }
}
