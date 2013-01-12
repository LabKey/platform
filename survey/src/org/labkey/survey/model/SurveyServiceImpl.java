package org.labkey.survey.model;

import org.labkey.api.data.Container;
import org.labkey.api.survey.SurveyService;
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
}
