package org.labkey.api.wiki;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.WebPartView;

/**
 * User: Mark Igra
 * Date: Jun 12, 2006
 * Time: 2:48:54 PM
 */
public class WikiService
{
    private static Service _serviceImpl;

    public interface Service
    {
        public WebPartView getView(Container c, String name, boolean forceRefresh, boolean renderContentOnly);
        public String getHtml(Container c, String name, boolean forceRefresh);
        public void insertWiki(User user, Container container, String name, String content, WikiRendererType renderType, String title);

        /**
         * Register a provider of macros.
         * @param name For macros of form {module:viewName} this is the module name
         * @param provider
         */
        public void registerMacroProvider(String name, MacroProvider provider);

        public WikiRenderer getRenderer(WikiRendererType rendererType);
        public WikiRenderer getRenderer(WikiRendererType rendererType, String attachPrefix, Attachment[] attachments);
        public WikiRendererType getDefaultWikiRendererType();
        public WikiRendererType getDefaultMessageRendererType();
    }

    public static void register(Service serviceImpl)
    {
        if (_serviceImpl != null)
            throw new IllegalStateException("Service has already been set.");
        _serviceImpl = serviceImpl;
    }

    public static class NoWikiServiceException extends IllegalStateException
    {
        private NoWikiServiceException()
        {
            super("Service has not been set");
        }
    }

    public static Service get()
    {
        if (_serviceImpl == null)
            throw new NoWikiServiceException();
        return _serviceImpl;
    }
}
