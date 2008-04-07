package org.labkey.api.exp.api;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;

/**
 * User: jeckels
 * Date: Jan 27, 2008
 */
public interface ExperimentUrls extends UrlProvider
{
    ActionURL getRunGraphURL(ExpRun run);
    ActionURL getRunGraphURL(Container c, int rowId);

    ActionURL getRunTextURL(Container c, int rowId);
    ActionURL getRunTextURL(ExpRun run);

    ActionURL getDeleteDatasURL(Container container, ActionURL returnURL);
    
    ActionURL getExperimentDetailsURL(Container c, ExpExperiment expExperiment);

    ActionURL getRemoveSelectedExpRunsURL(Container container, ActionURL returnURL, ExpExperiment expExperiment);

    ActionURL getExportRunsOptionsURL(Container container, ExpExperiment expExperiment);

    ActionURL getExportProtocolOptionsURL(Container container, ExpProtocol protocol);

    ActionURL getMoveRunsLocationURL(Container container);

    ActionURL getDeleteSelectedExpRunsURL(Container container, ActionURL returnURL);

    ActionURL getCreateRunGroupURL(Container container, ActionURL returnURL, boolean addSelectedRuns);

    ActionURL getAddRunsToExperimentURL(Container container, ExpExperiment expExperiment);

}
