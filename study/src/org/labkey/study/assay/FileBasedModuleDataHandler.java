package org.labkey.study.assay;

import org.labkey.api.exp.api.*;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.Lsid;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.study.assay.AssayUrls;
import org.apache.log4j.Logger;

import java.io.File;

/**
 * User: jeckels
 * Date: Dec 22, 2009
 */
public class FileBasedModuleDataHandler extends AbstractExperimentDataHandler
{
    public void deleteData(ExpData data, Container container, User user)
    {
        // We don't import these data files directly so no need to delete them
    }

    public ActionURL getContentURL(Container container, ExpData data)
    {
        ExpRun run = data.getRun();
        if (run != null)
        {
            ExpProtocol protocol = run.getProtocol();
            ExpProtocol p = ExperimentService.get().getExpProtocol(protocol.getRowId());
            return PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(container, p, run.getRowId());
        }
        return null;
    }

    public void importFile(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context) throws ExperimentException
    {
    }

    public void runMoved(ExpData newData, Container container, Container targetContainer, String oldRunLSID, String newRunLSID, User user, int oldDataRowID) throws ExperimentException
    {
        // We don't import these data files directly so no need to delete them
    }

    public Priority getPriority(ExpData object)
    {
        if (ModuleAssayProvider.RAW_DATA_TYPE.matches(new Lsid(object.getLSID())))
        {
            return Priority.HIGH;
        }
        return null;
    }
}
