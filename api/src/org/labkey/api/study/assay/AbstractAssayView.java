package org.labkey.api.study.assay;

import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.ACL;
import org.labkey.api.study.actions.AssayHeaderView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.VBox;

/**
 * User: jeckels
 * Date: Apr 15, 2009
 */
public class AbstractAssayView extends VBox
{
    protected void setupViews(QueryView queryView, boolean minimizeLinks, AssayProvider provider, ExpProtocol protocol)
    {
        AssayHeaderView headerView = new AssayHeaderView(protocol, provider, minimizeLinks, queryView.getTable().getContainerFilter());
        if (minimizeLinks)
        {
            queryView.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
            queryView.setShowRecordSelectors(false);
        }
        else
        {
            queryView.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTH);
        }

        addView(headerView);

        Container container = getViewContext().getContainer();
        if (!PipelineService.get().hasValidPipelineRoot(container))
        {
            StringBuilder html = new StringBuilder();
            html.append("<b>Pipeline root has not been set.</b> ");
            if (container.hasPermission(getViewContext().getUser(), ACL.PERM_ADMIN))
            {
                ActionURL url = PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(container);
                html.append("[<a href=\"").append(url.getLocalURIString()).append("\">setup pipeline</a>]");
            }
            else
                html.append(" Please ask an administrator for assistance.");
            addView(new HtmlView(html.toString()));
        }

        addView(queryView);
    }
    
}
