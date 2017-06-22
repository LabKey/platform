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
package org.labkey.api.survey;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.survey.model.Survey;
import org.labkey.api.survey.model.SurveyDesign;
import org.labkey.api.survey.model.SurveyListener;

/**
 * User: cnathe
 * Date: 1/11/13
 */
public interface SurveyService
{
    static SurveyService get()
    {
        return ServiceRegistry.get(SurveyService.class);
    }

    static void setInstance(SurveyService impl)
    {
        ServiceRegistry.get().registerService(SurveyService.class, impl);
    }

    Survey[] getSurveys(Container container, User user);
    Survey getSurvey(Container container, User user, int surveyId);
    Survey getSurvey(Container container, User user, String schema, String query, String responsePk);
    Survey saveSurvey(Container container, User user, Survey survey);
    SurveyDesign[] getSurveyDesigns(Container container, ContainerFilter filter);
    SurveyDesign[] getSurveyDesigns(SimpleFilter filter);
    SurveyDesign getSurveyDesign(Container container, User user, int surveyDesignId);
    SurveyDesign saveSurveyDesign(Container container, User user, SurveyDesign surveyDesign);
    void deleteSurveyDesign(Container c, User user, int surveyDesignId, boolean deleteSurveyInstances);
    void addSurveyListener(SurveyListener listener);
}
