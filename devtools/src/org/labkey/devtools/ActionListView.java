package org.labkey.devtools;

import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.SpringActionController.ActionDescriptor;
import org.labkey.api.data.Container;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;

import java.io.PrintWriter;
import java.util.Comparator;
import java.util.function.Predicate;

public class ActionListView extends HttpView
{
    private final SpringActionController _controller;
    private final Predicate<ActionDescriptor> _filter;

    public ActionListView(SpringActionController controller, Predicate<ActionDescriptor> filter)
    {
        _controller = controller;
        _filter = filter;
    }

    @Override
    protected void renderInternal(Object model, PrintWriter out)
    {
        Container c = _controller.getViewContext().getContainer();

        _controller.getActionResolver().getActionDescriptors().stream()
            .filter(ad->!(FormHandlerAction.class.isAssignableFrom(ad.getActionClass())))
            .filter(_filter::test)
            .sorted(Comparator.comparing(ActionDescriptor::getPrimaryName))
            .forEach(ad->{
                out.println(PageFlowUtil.link(ad.getPrimaryName()).href(new ActionURL(ad.getActionClass(), c)).clearClasses());
                out.print("<br>");
            });
    }
}
