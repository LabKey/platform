package org.labkey.experiment.controllers.list;

import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.view.*;

import java.util.Map;

/**
 * User: adam
 * Date: Nov 11, 2007
 * Time: 4:39:47 PM
 */
public class SingleListWebPartFactory extends WebPartFactory
{
    public SingleListWebPartFactory()
    {
        super("Single List", null, true, true);
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        Map<String, String> props = webPart.getPropertyMap();

        String listIdParam = props.get("listId");
        String title = (null == props.get("title") ? "Single List" : props.get("title"));

        if (null == listIdParam)
            return new HtmlView(title, "There is no list selected to be displayed in this webpart");

        try
        {
            ListQueryForm form = new ListQueryForm(Integer.parseInt(listIdParam), portalCtx.getUser(), portalCtx.getContainer());
            return new SingleListWebPart(form, props);
        }
        catch (NumberFormatException e)
        {
            return new HtmlView(title, "List id is invalid");
        }
        catch (NotFoundException e)
        {
            return new HtmlView(title, "List does not exist");
        }
    }

    public HttpView getEditView(Portal.WebPart webPart)
    {
        return new JspView<Portal.WebPart>("/org/labkey/experiment/controllers/list/customizeSingleListWebPart.jsp", webPart);
    }

    private static class SingleListWebPart extends ListQueryView
    {
        private SingleListWebPart(ListQueryForm form, Map<String, String> props)
        {
            super(form);

            ListDefinition list = form.getList();
            String title = props.get("title");

            if (null == title)
                title = list.getName();

            setTitle(title);
            setTitleHref(list.urlShowData());

            QuerySettings settings = getSettings();
            settings.setAllowChooseQuery(false);
            settings.setAllowChooseView(false);
        }
    }
}
