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
                                       boolean handleSubstitutions,
                                       String attachPrefix,
                                       Collection<? extends Attachment> attachments)
    {
        return HtmlStringBuilder.of(WIKI_PREFIX)
            .append(getRenderer(rendererType, handleSubstitutions, null, attachPrefix, null, attachments, sourceDescription).format(source).getHtml())
            .append(WIKI_SUFFIX).getHtmlString();
    }

    @Override
    public HtmlString getFormattedHtml(WikiRendererType rendererType, String source, @Nullable String sourceDescription)
    {
        return getFormattedHtml(rendererType, source, sourceDescription, false, null, null);
    }

    @Override
    public WikiRenderer getRenderer(WikiRendererType rendererType, boolean handleSubstitutions, String hrefPrefix,
                                    String attachPrefix, Map<String, String> nameTitleMap,
                                    Collection<? extends Attachment> attachments, String sourceDescription)
    {
        return switch (rendererType)
        {
            case RADEOX -> new RadeoxRenderer(hrefPrefix, attachPrefix, nameTitleMap, attachments, sourceDescription);
            case HTML -> new HtmlRenderer(handleSubstitutions, hrefPrefix, attachPrefix, nameTitleMap, attachments);
            case TEXT_WITH_LINKS -> new PlainTextRenderer();
            case MARKDOWN -> new MarkdownRenderer(hrefPrefix, attachPrefix, nameTitleMap, attachments);
        };
    }
}
