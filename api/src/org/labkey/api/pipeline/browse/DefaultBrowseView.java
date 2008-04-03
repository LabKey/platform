package org.labkey.api.pipeline.browse;

import org.labkey.api.view.HttpView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.PageFlowUtil;

import java.io.PrintWriter;

public class DefaultBrowseView <FORM extends BrowseForm> extends HttpView
{
    FORM form;
    BrowseView browseView;

    public DefaultBrowseView(FORM form)
    {
        super(form.getViewContext());
        this.form = form;
        this.browseView = PipelineService.get().getBrowseView(form);
    }


    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
        out.print(PageFlowUtil.getStrutsError(getViewContext().getRequest(), null));
        ActionURL formAction = getViewContext().getActionURL();
        out.write("<form method=\"POST\" action=\"" + PageFlowUtil.filter(formAction) + "\">");
        include(browseView, out);
        out.write("</form>");
    }
}
