package org.labkey.core.wiki;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.wiki.MacroProvider;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiRenderingService;
import org.radeox.macro.MacroRepository;

import java.util.Collection;
import java.util.Map;

public class WikiRenderingServiceImpl implements WikiRenderingService
{
    @Override
    public void registerMacroProvider(String name, MacroProvider provider)
    {
        MacroRepository repository = MacroRepository.getInstance();
        repository.put(name, new RadeoxMacroProxy(name, provider));
    }

    @Override
    public HtmlString getFormattedHtml(WikiRendererType rendererType,
                                       String source,
                                       @Nullable String sourceDescription,
                                       String attachPrefix,
                                       Collection<? extends Attachment> attachments)
    {
        return HtmlStringBuilder.of(WIKI_PREFIX)
            .append(getRenderer(rendererType, null, attachPrefix, null, attachments, sourceDescription).format(source).getHtml())
            .append(WIKI_SUFFIX).getHtmlString();
    }

    @Override
    public HtmlString getFormattedHtml(WikiRendererType rendererType, String source, @Nullable String sourceDescription)
    {
        return getFormattedHtml(rendererType, source, sourceDescription, null, null);
    }

    @Override
    public WikiRenderer getRenderer(WikiRendererType rendererType, String hrefPrefix,
                                    String attachPrefix, Map<String, String> nameTitleMap,
                                    Collection<? extends Attachment> attachments, String sourceDescription)
    {
        WikiRenderer renderer;

        switch (rendererType)
        {
            case RADEOX:
                renderer = new RadeoxRenderer(hrefPrefix, attachPrefix, nameTitleMap, attachments, sourceDescription);
                break;
            case HTML:
                renderer = new HtmlRenderer(hrefPrefix, attachPrefix, nameTitleMap, attachments);
                break;
            case TEXT_WITH_LINKS:
                renderer = new PlainTextRenderer();
                break;
            case MARKDOWN:
                renderer = new MarkdownRenderer(hrefPrefix, attachPrefix, nameTitleMap, attachments);
                break;
            default:
                renderer = new RadeoxRenderer(null, attachPrefix, null, attachments, sourceDescription);
        }

        return renderer;
    }
}
