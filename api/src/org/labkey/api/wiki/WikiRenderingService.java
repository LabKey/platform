package org.labkey.api.wiki;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.HtmlString;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public interface WikiRenderingService
{
    HtmlString WIKI_PREFIX = HtmlString.unsafe("<div class=\"labkey-wiki\">");
    HtmlString WIKI_SUFFIX = HtmlString.unsafe("</div>");

    static @NotNull WikiRenderingService get()
    {
        return Objects.requireNonNull(ServiceRegistry.get().getService(WikiRenderingService.class));
    }

    static void setInstance(WikiRenderingService impl)
    {
        ServiceRegistry.get().registerService(WikiRenderingService.class, impl);
    }

    /**
     * Register a provider of macros.
     * @param name For macros of form {module:viewName} this is the module name
     * @param provider A macro provider
     */
    void registerMacroProvider(String name, MacroProvider provider);

    /**
     * @param sourceDescription info on where the text came from for debugging purposes. For example: Announcement 6654 in /MyContainer
     */
    HtmlString getFormattedHtml(WikiRendererType rendererType, String source, @Nullable String sourceDescription);

    /**
     * @param sourceDescription info on where the text came from for debugging purposes. For example: Announcement 6654 in /MyContainer
     */
    HtmlString getFormattedHtml(WikiRendererType rendererType, String source, @Nullable String sourceDescription,
                                String attachPrefix, Collection<? extends Attachment> attachments);

    /**
     * @param sourceDescription info on where the text came from for debugging purposes. For example: Announcement 6654 in /MyContainer
     */
    WikiRenderer getRenderer(WikiRendererType rendererType, String hrefPrefix,
                             String attachPrefix, Map<String, String> nameTitleMap,
                             Collection<? extends Attachment> attachments,
                             String sourceDescription);
}
