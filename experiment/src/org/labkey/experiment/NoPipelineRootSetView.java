package org.labkey.experiment;

import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HtmlView;

/**
 * User: jeckels
 * Date: Nov 21, 2007
 */
public class NoPipelineRootSetView extends HtmlView
{
    public NoPipelineRootSetView(Container c, String actionDescription)
    {
        super("Your project must have a <a href=\"" + PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(c).getLocalURIString() + "\">Pipeline Root</a> set before you can " + actionDescription + ".");
    }
}
