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
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.CoreHtmlNodeRenderer;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.HtmlBlock;
import org.labkey.api.markdown.MarkdownService;

import java.util.List;
import java.util.Set;

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
            .softbreak("<br>\n")  // See Issue #34169
            .sanitizeUrls(true)
            .escapeHtml(true)
            .nodeRendererFactory(CommentNodeRenderer::new)
            .extensions(extensions)
            .build();
    }

    private static class CommentNodeRenderer extends CoreHtmlNodeRenderer
    {
        private final HtmlNodeRendererContext _context;

        public CommentNodeRenderer(HtmlNodeRendererContext context)
        {
            super(context);
            _context = context;
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes()
        {
            return Set.of(HtmlInline.class, HtmlBlock.class);
        }

        @Override
        public void render(Node node)
        {
            if (node instanceof HtmlInline inline)
            {
                String literal = inline.getLiteral();
                if (isComment(literal))
                {
                    _context.getWriter().raw(literal);
                }
                else
                {
                    _context.getWriter().text(literal);
                }
            }
            else if (node instanceof HtmlBlock block)
            {
                String literal = block.getLiteral();
                if (isComment(literal))
                {
                    _context.getWriter().raw(literal);
                }
                else
                {
                    _context.getWriter().tag("p");
                    _context.getWriter().text(literal);
                    _context.getWriter().tag("/p");
                    _context.getWriter().line();
                }
            }
        }

        private boolean isComment(String literal)
        {
            return literal != null && literal.trim().startsWith("<!--") && literal.trim().endsWith("-->");
        }
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
