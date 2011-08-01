package org.labkey.api.study;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

/**
 * User: Nick
 * Date: 7/22/11
 */
public interface StudyUrls extends UrlProvider
{
    ActionURL getCreateStudyURL(Container container);
    ActionURL getManageStudyURL(Container container);
    ActionURL getStudyOverviewURL(Container container);
}
