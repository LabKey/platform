package org.labkey.wiki;

import org.labkey.api.data.Container;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;

import java.util.Map;

/**
 * User: adam
 * Date: Oct 13, 2010
 * Time: 4:38:09 PM
 */
public class RenderedWikiResource extends WikiWebdavProvider.WikiPageResource
{
    public RenderedWikiResource(Container c, String name, String entityId, String body, WikiRendererType rendererType, Map<String, Object> m)
    {
        super(c, name, entityId, body, rendererType, m);
    }

    @Override
    protected void setBody(String body)
    {
        super.setBody(getHtml(body, _type));
    }

    @Override
    public String getContentType()
    {
        return "text/html";
    }

    // TODO: Send plain text wikis straight through instead of rendering to HTML then parsing?
    private String getHtml(String body, WikiRendererType type)
    {
        WikiService service = ServiceRegistry.get().getService(WikiService.class);

        if (null == service)
            throw new IllegalStateException("WikiService not found");

        return service.getRenderer(type).format(body).getHtml();
    }
}
