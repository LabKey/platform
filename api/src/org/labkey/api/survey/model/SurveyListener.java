/*
 * Copyright (c) 2013-2018 LabKey Corporation
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
package org.labkey.api.survey.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: 1/28/13
 */
public interface SurveyListener
{
    /**
     * Invoked before the survey is deleted
     * @param c
     * @param user
     * @param survey
     * @throws Exception
     */
    void surveyBeforeDelete(Container c, User user, Survey survey);

    /**
     * Invoked when a survey is deleted
     * @param c
     * @param user
     * @param survey
     * @throws Exception
     */
    void surveyDeleted(Container c, User user, Survey survey);

    /**
     * Invoked when a survey is created
     *
     * @param rowData the data representing the new survey
     * @throws Exception
     */
    void surveyCreated(Container c, User user, Survey survey, Map<String, Object> rowData);

    /**
     * Invoked when a survey is updated
     *
     * @param oldRow the row data before the update
     * @param rowData the row data after the update
     * @throws Exception
     */
    void surveyUpdated(Container c, User user, Survey survey, @Nullable Map<String, Object> oldRow, Map<String, Object> rowData);

    /**
     * Invoked before the responses associated with the specified survey are changed.
     * @param c
     * @param user
     * @param survey
     * @throws Exception
     */
    void surveyResponsesBeforeUpdate(Container c, User user, Survey survey);

    /**
     * Invoked when the responses associated with the specified survey are changed.
     * @param c
     * @param user
     * @param survey
     * @param rowData the responses (survey answers) that have been modified.
     * @throws Exception
     */
    void surveyResponsesUpdated(Container c, User user, Survey survey, Map<String, Object> rowData);

    /**
     * Allow survey subclasses to define locked states
     *
     */
    List<String> getSurveyLockedStates();
}
