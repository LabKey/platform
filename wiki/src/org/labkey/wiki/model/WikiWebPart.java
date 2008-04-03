package org.labkey.wiki.model;

import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Container;
import org.labkey.api.view.ViewContext;

import javax.servlet.ServletException;
import java.util.Map;

/**
 * User: adam
 * Date: Aug 11, 2007
 * Time: 3:30:55 PM
 */
public class WikiWebPart extends BaseWikiView
{
    public WikiWebPart(String pageId, int index, Map<String, String> props)
    {
        super();
        _pageId = pageId;
        _index = index;

        // webPartContainer and name will be null in the new webpart case
        String containerId = props.get("webPartContainer");
        Container c = (null != containerId ? ContainerManager.getForId(props.get("webPartContainer")) : getViewContext().getContainer());

        String name = props.get("name");
        name = (name != null) ? name : "default";

        init(c, name, true);
    }


    @Override
    protected void prepareWebPart(Object model) throws ServletException
    {
        ViewContext context = getViewContext();
        boolean removeLinks = isEmbedded() && getFrame() == FrameType.NONE;
        context.put("includeLinks", !removeLinks);
        context.put("isEmbedded", isEmbedded());

        super.prepareWebPart(model);
    }
}
