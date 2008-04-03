package org.labkey.api.exp.api;

import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Handler;
import org.labkey.api.util.URLHelper;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewBackgroundInfo;
import org.apache.log4j.Logger;

import java.io.File;

/**
 * User: jeckels
 * Date: Jul 10, 2007
 */
public class DefaultExperimentDataHandler extends AbstractExperimentDataHandler
{
    public void importFile(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context) throws ExperimentException
    {
        log.info("No ExperimentDataHandler registered for data file " + data.getDataFileURI() + ", no special loading will be done on this file.");
    }

    public URLHelper getContentURL(Container container, ExpData data)
    {
        return null;
    }

    public void deleteData(ExpData data, Container container, User user) throws ExperimentException
    {
        // Do nothing
    }

    public void runMoved(ExpData newData, Container container, Container targetContainer, String oldRunLSID, String newRunLSID, User user, int oldDataRowID) throws ExperimentException
    {
        // Do nothing
    }

    public Handler.Priority getPriority(ExpData data)
    {
        return Handler.Priority.LOW;
    }
}
