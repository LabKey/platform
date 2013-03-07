package org.labkey.api.survey;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 3/6/13
 */
public interface SurveyUrls extends UrlProvider
{
    ActionURL getUpdateSurveyAction(Container container, int surveyId, int surveyDesignId);
    ActionURL getSurveyDesignAction(Container container, int surveyDesignId);
}
