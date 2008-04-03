package org.labkey.api.study.assay;

import org.labkey.api.view.*;
import org.labkey.api.study.actions.AssayHeaderView;
import org.labkey.api.query.QueryView;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.data.*;

/**
 * User: brittp
 * Date: Aug 21, 2007
 * Time: 9:30:03 AM
 */
public class AssayRunsView extends VBox
{
    public AssayRunsView(ExpProtocol protocol, boolean minimizeLinks)
    {
        AssayHeaderView headerView = new AssayHeaderView(protocol, AssayService.get().getProvider(protocol), minimizeLinks);
        ViewContext context = getViewContext();
        AssayProvider provider = AssayService.get().getProvider(protocol);
        QueryView runsView = provider.createRunView(context, protocol);
        if (minimizeLinks)
        {
            runsView.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
        }
        else
        {
            runsView.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTH);
        }

        addView(headerView);

        if (!provider.allowUpload(context.getUser(), context.getContainer(), protocol))
            addView(provider.getDisallowedUploadMessageView(context.getUser(), context.getContainer(), protocol));

        addView(runsView);
    }
}
