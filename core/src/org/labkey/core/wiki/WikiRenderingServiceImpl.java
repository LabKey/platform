package org.labkey.core.wiki;

import org.labkey.api.attachments.Attachment;
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
    public String getFormattedHtml(WikiRendererType rendererType, String source, String attachPrefix, Collection<? extends Attachment> attachments)
    {
        return WIKI_PREFIX + getRenderer(rendererType, null, attachPrefix, null, attachments).format(source).getHtml() + WIKI_SUFFIX;
    }

    @Override
    public String getFormattedHtml(WikiRendererType rendererType, String source)
    {
        return getFormattedHtml(rendererType, source, null, null);
    }

    @Override
    public WikiRenderer getRenderer(WikiRendererType rendererType, String hrefPrefix,
                                    String attachPrefix, Map<String, String> nameTitleMap,
                                    Collection<? extends Attachment> attachments)
    {
        WikiRenderer renderer;

        switch (rendererType)
        {
            case RADEOX:
                renderer = new RadeoxRenderer(hrefPrefix, attachPrefix, nameTitleMap, attachments);
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
                renderer = new RadeoxRenderer(null, attachPrefix, null, attachments);
        }

        return renderer;
    }
}
