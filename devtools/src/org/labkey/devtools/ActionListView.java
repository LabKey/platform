package org.labkey.devtools;

import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ActionListView extends HttpView
{
    private final SpringActionController _controller;

    public ActionListView(SpringActionController controller)
    {
        _controller = controller;
    }

    @Override
    protected void renderInternal(Object model, PrintWriter out)
    {
        List<SpringActionController.ActionDescriptor> descriptors = new ArrayList<>(_controller.getActionResolver().getActionDescriptors());
        descriptors.sort(Comparator.comparing(SpringActionController.ActionDescriptor::getPrimaryName));
        Container c = _controller.getViewContext().getContainer();

        for (SpringActionController.ActionDescriptor ad : descriptors)
        {
            out.println(PageFlowUtil.link(ad.getPrimaryName()).href(new ActionURL(ad.getActionClass(), c)).clearClasses());
            out.print("<br>");
        }
    }
}
