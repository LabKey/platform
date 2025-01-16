/*
 * Copyright (c) 2017-2019 LabKey Corporation
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

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.ext.image.attributes.ImageAttributesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.labkey.api.markdown.MarkdownService;

import java.util.List;

public class MarkdownServiceImpl implements MarkdownService
{
    // From the commonmark-java docs: "Both the Parser and HtmlRenderer are designed so that you can configure them
    // once using the builders and then use them multiple times/from multiple threads." So that's what we're doing.
    // See https://github.com/commonmark/commonmark-java?tab=readme-ov-file#thread-safety
    private final Parser _parser;
    private final HtmlRenderer _renderer;

    public MarkdownServiceImpl()
    {
        // Base commonmark-java plus Autolink, Strikethrough, and Tables extensions get us close to parity with our old
        // markdown-it.js implementation. The Heading Anchor and Image Attributes extensions were requested by
        // the docs team.
        List<Extension> extensions = List.of(
            AutolinkExtension.create(),
            HeadingAnchorExtension.create(),
            ImageAttributesExtension.create(),
            StrikethroughExtension.create(),
            TablesExtension.create()
        );
        _parser = Parser.builder()
            .extensions(extensions)
            .build();
        _renderer = HtmlRenderer.builder()
            .softbreak("<br>\n")
            .extensions(extensions)
            .build();
    }

    @Override
    public String toHtml(String mdText)
    {
        if (null == mdText)
            mdText = "";

        Node document = _parser.parse(mdText);
        String html = _renderer.render(document);

        // #32468 include selector so we can have markdown-specific styling namespace
        return "<div class=\"lk-markdown-container\">" + html + "</div>";
    }
}
