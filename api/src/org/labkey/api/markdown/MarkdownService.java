/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.markdown;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.test.TestWhen;

import javax.script.ScriptException;

/**
 * Utility service to convert Markdown-formatted text to HTML
 * User: Jim Piper
 * Date: Jun 28, 2017
 */
public interface MarkdownService
{
    /**
     * @return the html string that will render the content described by the markdown text of the input string
     */
    String toHtml(String mdText) throws NoSuchMethodException, ScriptException;

    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {

        /**
         * Test that MarkdownService correctly translates markdown of headings to html
         */
        @Test
        public void testMdHeadingToHtml() throws Exception
        {
            MarkdownService markdownService = ServiceRegistry.get().getService(MarkdownService.class);
            String testMdText = "# This is a H1 header";
            String expectedHtmlText = "<h1>This is a H1 header</h1>\n";
            String htmlText = markdownService.toHtml(testMdText);
            assertTrue("The MarkdownService failed to correctly translate markdown of heading 1 text to html.", expectedHtmlText.equals(htmlText));
        }

        /**
         * Test that MarkdownService correctly translates markdown of bold to html
         */
        @Test
        public void testMdBoldToHtml() throws Exception
        {
            MarkdownService markdownService = ServiceRegistry.get().getService(MarkdownService.class);
            String testMdText = "**This is bold text**";
            String expectedHtmlText = "<p><strong>This is bold text</strong></p>\n";
            String htmlText = markdownService.toHtml(testMdText);
            assertTrue("The MarkdownService failed to correctly translate markdown of bold text to html.", expectedHtmlText.equals(htmlText));
        }

        /**
         * Test that MarkdownService correctly translates complex markdown to html
         */
        @Test
        public void testMdComplexToHtml() throws Exception
        {
            MarkdownService markdownService = ServiceRegistry.get().getService(MarkdownService.class);
            // this sample of markdown and translation taken from part of: https://markdown-it.github.io/
            String testMdText = "---\n" +
                    "\n" +
                    "- __[pica](https://nodeca.github.io/pica/demo/)__ - high quality and fast image\n" +
                    "  resize in browser.\n" +
                    "- __[babelfish](https://github.com/nodeca/babelfish/)__ - developer friendly\n" +
                    "  i18n with plurals support and easy syntax.\n" +
                    "\n" +
                    "You will like those projects!\n" +
                    "\n" +
                    "---\n" +
                    "\n" +
                    "# h1 Heading\n" +
                    "## h2 Heading\n" +
                    "### h3 Heading\n" +
                    "#### h4 Heading\n" +
                    "##### h5 Heading\n" +
                    "###### h6 Heading\n" +
                    "\n" +
                    "\n" +
                    "## Horizontal Rules\n" +
                    "\n" +
                    "___\n" +
                    "\n" +
                    "---\n" +
                    "\n" +
                    "***\n" +
                    "\n" +
                    "\n" +
                    "## Typographic replacements\n" +
                    "\n" +
                    "Enable typographer option to see result.\n" +
                    "\n" +
                    "(c) (C) (r) (R) (tm) (TM) (p) (P) +-\n" +
                    "\n" +
                    "test.. test... test..... test?..... test!....\n" +
                    "\n" +
                    "!!!!!! ???? ,,  -- ---\n" +
                    "\n" +
                    "\"Smartypants, double quotes\" and 'single quotes'\n" +
                    "\n" +
                    "\n" +
                    "## Emphasis\n" +
                    "\n" +
                    "**This is bold text**\n" +
                    "\n" +
                    "__This is bold text__\n" +
                    "\n" +
                    "*This is italic text*\n" +
                    "\n" +
                    "_This is italic text_\n" +
                    "\n" +
                    "~~Strikethrough~~\n" +
                    "\n" +
                    "\n" +
                    "## Blockquotes\n" +
                    "\n" +
                    "\n" +
                    "> Blockquotes can also be nested...\n" +
                    ">> ...by using additional greater-than signs right next to each other...\n" +
                    "> > > ...or with spaces between arrows.\n" +
                    "\n" +
                    "\n" +
                    "## Lists\n" +
                    "\n" +
                    "Unordered\n" +
                    "\n" +
                    "+ Create a list by starting a line with `+`, `-`, or `*`\n" +
                    "+ Sub-lists are made by indenting 2 spaces:\n" +
                    "  - Marker character change forces new list start:\n" +
                    "    * Ac tristique libero volutpat at\n" +
                    "    + Facilisis in pretium nisl aliquet\n" +
                    "    - Nulla volutpat aliquam velit\n" +
                    "+ Very easy!\n" +
                    "\n" +
                    "Ordered\n" +
                    "\n" +
                    "1. Lorem ipsum dolor sit amet\n" +
                    "2. Consectetur adipiscing elit\n" +
                    "3. Integer molestie lorem at massa\n" +
                    "\n" +
                    "\n" +
                    "1. You can use sequential numbers...\n" +
                    "1. ...or keep all the numbers as `1.`\n" +
                    "\n" +
                    "Start numbering with offset:\n" +
                    "\n" +
                    "57. foo\n" +
                    "1. bar\n" +
                    "\n" +
                    "\n" +
                    "## Code\n" +
                    "\n" +
                    "Inline `code`\n" +
                    "\n" +
                    "Indented code\n" +
                    "\n" +
                    "    // Some comments\n" +
                    "    line 1 of code\n" +
                    "    line 2 of code\n" +
                    "    line 3 of code\n" +
                    "\n" +
                    "\n" +
                    "## Tables\n" +
                    "\n" +
                    "| Option | Description |\n" +
                    "| ------ | ----------- |\n" +
                    "| data   | path to data files to supply the data that will be passed into templates. |\n" +
                    "| engine | engine to be used for processing templates. Handlebars is the default. |\n" +
                    "| ext    | extension to be used for dest files. |\n" +
                    "\n" +
                    "Right aligned columns\n" +
                    "\n" +
                    "| Option | Description |\n" +
                    "| ------:| -----------:|\n" +
                    "| data   | path to data files to supply the data that will be passed into templates. |\n" +
                    "| engine | engine to be used for processing templates. Handlebars is the default. |\n" +
                    "| ext    | extension to be used for dest files. |\n" +
                    "\n" +
                    "\n" +
                    "## Links\n" +
                    "\n" +
                    "[link text](http://dev.nodeca.com)\n" +
                    "\n" +
                    "[link with title](http://nodeca.github.io/pica/demo/ \"title text!\")\n" +
                    "\n" +
                    "Autoconverted link https://github.com/nodeca/pica (enable linkify to see)\n";
            String expectedHtmlText = "<hr>\n" +
                    "<ul>\n" +
                    "<li><strong><a href=\"https://nodeca.github.io/pica/demo/\">pica</a></strong> - high quality and fast image\n" +
                    "resize in browser.</li>\n" +
                    "<li><strong><a href=\"https://github.com/nodeca/babelfish/\">babelfish</a></strong> - developer friendly\n" +
                    "i18n with plurals support and easy syntax.</li>\n" +
                    "</ul>\n" +
                    "<p>You will like those projects!</p>\n" +
                    "<hr>\n" +
                    "<h1>h1 Heading</h1>\n" +
                    "<h2>h2 Heading</h2>\n" +
                    "<h3>h3 Heading</h3>\n" +
                    "<h4>h4 Heading</h4>\n" +
                    "<h5>h5 Heading</h5>\n" +
                    "<h6>h6 Heading</h6>\n" +
                    "<h2>Horizontal Rules</h2>\n" +
                    "<hr>\n" +
                    "<hr>\n" +
                    "<hr>\n" +
                    "<h2>Typographic replacements</h2>\n" +
                    "<p>Enable typographer option to see result.</p>\n" +
                    "<p>(c) (C) (r) (R) (tm) (TM) (p) (P) +-</p>\n" +
                    "<p>test.. test... test..... test?..... test!....</p>\n" +
                    "<p>!!!!!! ???? ,,  -- ---</p>\n" +
                    "<p>&quot;Smartypants, double quotes&quot; and 'single quotes'</p>\n" +
                    "<h2>Emphasis</h2>\n" +
                    "<p><strong>This is bold text</strong></p>\n" +
                    "<p><strong>This is bold text</strong></p>\n" +
                    "<p><em>This is italic text</em></p>\n" +
                    "<p><em>This is italic text</em></p>\n" +
                    "<p><s>Strikethrough</s></p>\n" +
                    "<h2>Blockquotes</h2>\n" +
                    "<blockquote>\n" +
                    "<p>Blockquotes can also be nested...</p>\n" +
                    "<blockquote>\n" +
                    "<p>...by using additional greater-than signs right next to each other...</p>\n" +
                    "<blockquote>\n" +
                    "<p>...or with spaces between arrows.</p>\n" +
                    "</blockquote>\n" +
                    "</blockquote>\n" +
                    "</blockquote>\n" +
                    "<h2>Lists</h2>\n" +
                    "<p>Unordered</p>\n" +
                    "<ul>\n" +
                    "<li>Create a list by starting a line with <code>+</code>, <code>-</code>, or <code>*</code></li>\n" +
                    "<li>Sub-lists are made by indenting 2 spaces:\n" +
                    "<ul>\n" +
                    "<li>Marker character change forces new list start:\n" +
                    "<ul>\n" +
                    "<li>Ac tristique libero volutpat at</li>\n" +
                    "</ul>\n" +
                    "<ul>\n" +
                    "<li>Facilisis in pretium nisl aliquet</li>\n" +
                    "</ul>\n" +
                    "<ul>\n" +
                    "<li>Nulla volutpat aliquam velit</li>\n" +
                    "</ul>\n" +
                    "</li>\n" +
                    "</ul>\n" +
                    "</li>\n" +
                    "<li>Very easy!</li>\n" +
                    "</ul>\n" +
                    "<p>Ordered</p>\n" +
                    "<ol>\n" +
                    "<li>\n" +
                    "<p>Lorem ipsum dolor sit amet</p>\n" +
                    "</li>\n" +
                    "<li>\n" +
                    "<p>Consectetur adipiscing elit</p>\n" +
                    "</li>\n" +
                    "<li>\n" +
                    "<p>Integer molestie lorem at massa</p>\n" +
                    "</li>\n" +
                    "<li>\n" +
                    "<p>You can use sequential numbers...</p>\n" +
                    "</li>\n" +
                    "<li>\n" +
                    "<p>...or keep all the numbers as <code>1.</code></p>\n" +
                    "</li>\n" +
                    "</ol>\n" +
                    "<p>Start numbering with offset:</p>\n" +
                    "<ol start=\"57\">\n" +
                    "<li>foo</li>\n" +
                    "<li>bar</li>\n" +
                    "</ol>\n" +
                    "<h2>Code</h2>\n" +
                    "<p>Inline <code>code</code></p>\n" +
                    "<p>Indented code</p>\n" +
                    "<pre><code>// Some comments\n" +
                    "line 1 of code\n" +
                    "line 2 of code\n" +
                    "line 3 of code\n" +
                    "</code></pre>\n" +
                    "<h2>Tables</h2>\n" +
                    "<table>\n" +
                    "<thead>\n" +
                    "<tr>\n" +
                    "<th>Option</th>\n" +
                    "<th>Description</th>\n" +
                    "</tr>\n" +
                    "</thead>\n" +
                    "<tbody>\n" +
                    "<tr>\n" +
                    "<td>data</td>\n" +
                    "<td>path to data files to supply the data that will be passed into templates.</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td>engine</td>\n" +
                    "<td>engine to be used for processing templates. Handlebars is the default.</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td>ext</td>\n" +
                    "<td>extension to be used for dest files.</td>\n" +
                    "</tr>\n" +
                    "</tbody>\n" +
                    "</table>\n" +
                    "<p>Right aligned columns</p>\n" +
                    "<table>\n" +
                    "<thead>\n" +
                    "<tr>\n" +
                    "<th style=\"text-align:right\">Option</th>\n" +
                    "<th style=\"text-align:right\">Description</th>\n" +
                    "</tr>\n" +
                    "</thead>\n" +
                    "<tbody>\n" +
                    "<tr>\n" +
                    "<td style=\"text-align:right\">data</td>\n" +
                    "<td style=\"text-align:right\">path to data files to supply the data that will be passed into templates.</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td style=\"text-align:right\">engine</td>\n" +
                    "<td style=\"text-align:right\">engine to be used for processing templates. Handlebars is the default.</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td style=\"text-align:right\">ext</td>\n" +
                    "<td style=\"text-align:right\">extension to be used for dest files.</td>\n" +
                    "</tr>\n" +
                    "</tbody>\n" +
                    "</table>\n" +
                    "<h2>Links</h2>\n" +
                    "<p><a href=\"http://dev.nodeca.com\">link text</a></p>\n" +
                    "<p><a href=\"http://nodeca.github.io/pica/demo/\" title=\"title text!\">link with title</a></p>\n" +
                    "<p>Autoconverted link https://github.com/nodeca/pica (enable linkify to see)</p>\n";
            String htmlText = markdownService.toHtml(testMdText);
            assertTrue("The MarkdownService failed to correctly translate complex markdown text to html.", expectedHtmlText.equals(htmlText));
        }
    }

}
