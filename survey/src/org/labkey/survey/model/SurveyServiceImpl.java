/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.survey.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.security.User;
import org.labkey.api.survey.SurveyService;
import org.labkey.api.survey.model.Survey;
import org.labkey.api.survey.model.SurveyDesign;
import org.labkey.api.survey.model.SurveyListener;
import org.labkey.survey.SurveyManager;

/**
 * User: cnathe
 * Date: 1/11/13
 */
public class SurveyServiceImpl implements SurveyService
{
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
    public Survey getSurvey(Container container, User user, String schema, String query, String responsePk)
    {
        return SurveyManager.get().getSurvey(container, user, schema, query, responsePk);
    }

    @Override
    public SurveyDesign[] getSurveyDesigns(Container container, ContainerFilter filter)
    {
        return SurveyManager.get().getSurveyDesigns(container, filter);
    }

    @Override
    public SurveyDesign[] getSurveyDesigns(SimpleFilter filter)
    {
        return SurveyManager.get().getSurveyDesigns(filter);
    }

    @Override
    public SurveyDesign getSurveyDesign(Container container, User user, int surveyDesignId)
    {
        return SurveyManager.get().getSurveyDesign(container, user, surveyDesignId);
    }

    @Override
    public Survey saveSurvey(Container container, User user, Survey survey)
    {
        return SurveyManager.get().saveSurvey(container, user, survey);
    }

    @Override
    public SurveyDesign saveSurveyDesign(Container container, User user, SurveyDesign surveyDesign)
    {
        return SurveyManager.get().saveSurveyDesign(container, user, surveyDesign);
    }

    @Override
    public void deleteSurveyDesign(Container c, User user, int surveyDesignId, boolean deleteSurveyInstances)
    {
        SurveyManager.get().deleteSurveyDesign(c, user, surveyDesignId, deleteSurveyInstances);
    }

    @Override
    public void addSurveyListener(SurveyListener listener)
    {
        SurveyManager.addSurveyListener(listener);
    }
}
