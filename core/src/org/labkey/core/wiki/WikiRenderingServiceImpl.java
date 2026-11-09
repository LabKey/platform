/*
 * Copyright (c) 2019-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
                                       SubstitutionMode substitutionMode,
                                       String attachPrefix,
                                       Collection<? extends Attachment> attachments)
    {
        return HtmlStringBuilder.of(WIKI_PREFIX)
            .append(getRenderer(rendererType, substitutionMode, null, attachPrefix, null, attachments, sourceDescription).format(source).getHtml())
            .append(WIKI_SUFFIX).getHtmlString();
    }

    @Override
    public HtmlString getFormattedHtml(WikiRendererType rendererType, String source, @Nullable String sourceDescription)
    {
        return getFormattedHtml(rendererType, source, sourceDescription, SubstitutionMode.Ignore, null, null);
    }

    @Override
    public WikiRenderer getRenderer(WikiRendererType rendererType, SubstitutionMode substitutionMode, String hrefPrefix,
                                    String attachPrefix, Map<String, String> nameTitleMap,
                                    Collection<? extends Attachment> attachments, String sourceDescription)
    {
        return switch (rendererType)
        {
            case RADEOX -> new RadeoxRenderer(hrefPrefix, attachPrefix, nameTitleMap, attachments, sourceDescription);
            case HTML -> new HtmlRenderer(substitutionMode, hrefPrefix, attachPrefix, nameTitleMap, attachments);
            case TEXT_WITH_LINKS -> new PlainTextRenderer();
            case MARKDOWN -> new MarkdownRenderer(hrefPrefix, attachPrefix, nameTitleMap, attachments);
        };
    }
}
