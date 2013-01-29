package org.labkey.api.survey.model;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 1/28/13
 */
public interface SurveyListener
{
    void surveyDeleted(Container c, User user, Survey survey) throws Exception;
    void surveyCreated(Container c, User user, Survey survey) throws Exception;
    void surveyUpdated(Container c, User user, Survey survey) throws Exception;
}
