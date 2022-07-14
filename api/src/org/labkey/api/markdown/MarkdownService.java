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
package org.labkey.api.markdown;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.test.TestWhen;

import javax.script.ScriptException;
import java.util.Map;

/**
 * Utility service to convert Markdown-formatted text to HTML
 * User: Jim Piper
 * Date: Jun 28, 2017
 */
public interface MarkdownService
{
    enum Options
    {
        breaks,
        html,
        linkify
    }

    static @Nullable MarkdownService get()
    {
        return ServiceRegistry.get().getService(MarkdownService.class);
    }

    static void setInstance(MarkdownService impl)
    {
        ServiceRegistry.get().registerService(MarkdownService.class, impl);
    }

    /**
     * @return the html string that will render the content described by the markdown text of the input string
     */
    String toHtml(String mdText) throws NoSuchMethodException, ScriptException;
    String toHtml(String mdText, Map<Options,Boolean> options) throws NoSuchMethodException, ScriptException;

    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {
        /**
         * Test that MarkdownService correctly translates markdown of headings to html
         */
        @Test
        public void testMdHeadingToHtml() throws Exception
        {
            MarkdownService markdownService = MarkdownService.get();
            String testMdText = "# This is a H1 header";
            String expectedHtmlText = "<div class=\"lk-markdown-container\"><h1>This is a H1 header</h1>\n</div>";
            String htmlText = markdownService.toHtml(testMdText);
            assertEquals("The MarkdownService failed to correctly translate markdown of heading 1 text to html.", expectedHtmlText, htmlText);
        }

        /**
         * Test that MarkdownService correctly translates markdown of bold to html
         */
        @Test
        public void testMdBoldToHtml() throws Exception
        {
            MarkdownService markdownService = MarkdownService.get();
            String testMdText = "**This is bold text**";
            String expectedHtmlText = "<div class=\"lk-markdown-container\"><p><strong>This is bold text</strong></p>\n</div>";
            String htmlText = markdownService.toHtml(testMdText);
            assertEquals("The MarkdownService failed to correctly translate markdown of bold text to html.", expectedHtmlText, htmlText);
        }

        /**
         * Test html tags
         */
        @Test
        public void testMdHtmlTags() throws Exception
        {
            MarkdownService markdownService = MarkdownService.get();

            String testMdText = "<h2>header</h2>";
            String expectedHtmlText = "<div class=\"lk-markdown-container\"><h2>header</h2></div>";
            String htmlText = markdownService.toHtml(testMdText, Map.of(Options.html,true));
            assertEquals("The MarkdownService failed to correctly translate markdown with html tags.", expectedHtmlText, htmlText);

            testMdText = "<script>alert()</script>";
            expectedHtmlText = "<div class=\"lk-markdown-container\"><script>alert()</script></div>";
            htmlText = markdownService.toHtml(testMdText, Map.of(Options.html,true));
            assertEquals("The MarkdownService failed to correctly translate markdown with html tags.", expectedHtmlText, htmlText);
        }


        /**
         * Test that MarkdownService correctly translates complex markdown to html
         */
        @Test
        public void testMdComplexToHtml() throws Exception
        {
            MarkdownService markdownService = MarkdownService.get();
            // this sample of markdown and translation taken from part of: https://markdown-it.github.io/
            String testMdText = """
                    ---

                    - __[pica](https://nodeca.github.io/pica/demo/)__ - high quality and fast image
                      resize in browser.
                    - __[babelfish](https://github.com/nodeca/babelfish/)__ - developer friendly
                      i18n with plurals support and easy syntax.

                    You will like those projects!

                    ---

                    # h1 Heading
                    ## h2 Heading
                    ### h3 Heading
                    #### h4 Heading
                    ##### h5 Heading
                    ###### h6 Heading


                    ## Horizontal Rules

                    ___

                    ---

                    ***


                    ## Typographic replacements

                    Enable typographer option to see result.

                    (c) (C) (r) (R) (tm) (TM) (p) (P) +-

                    test.. test... test..... test?..... test!....

                    !!!!!! ???? ,,  -- ---

                    "Smartypants, double quotes" and 'single quotes'


                    ## Emphasis

                    **This is bold text**

                    __This is bold text__

                    *This is italic text*

                    _This is italic text_

                    ~~Strikethrough~~


                    ## Blockquotes


                    > Blockquotes can also be nested...
                    >> ...by using additional greater-than signs right next to each other...
                    > > > ...or with spaces between arrows.


                    ## Lists

                    Unordered

                    + Create a list by starting a line with `+`, `-`, or `*`
                    + Sub-lists are made by indenting 2 spaces:
                      - Marker character change forces new list start:
                        * Ac tristique libero volutpat at
                        + Facilisis in pretium nisl aliquet
                        - Nulla volutpat aliquam velit
                    + Very easy!

                    Ordered

                    1. Lorem ipsum dolor sit amet
                    2. Consectetur adipiscing elit
                    3. Integer molestie lorem at massa


                    1. You can use sequential numbers...
                    1. ...or keep all the numbers as `1.`

                    Start numbering with offset:

                    57. foo
                    1. bar


                    ## Code

                    Inline `code`

                    Indented code

                        // Some comments
                        line 1 of code
                        line 2 of code
                        line 3 of code


                    ## Tables

                    | Option | Description |
                    | ------ | ----------- |
                    | data   | path to data files to supply the data that will be passed into templates. |
                    | engine | engine to be used for processing templates. Handlebars is the default. |
                    | ext    | extension to be used for dest files. |

                    Right aligned columns

                    | Option | Description |
                    | ------:| -----------:|
                    | data   | path to data files to supply the data that will be passed into templates. |
                    | engine | engine to be used for processing templates. Handlebars is the default. |
                    | ext    | extension to be used for dest files. |


                    ## Links

                    [link text](http://dev.nodeca.com)

                    [link with title](http://nodeca.github.io/pica/demo/ "title text!")

                    Autoconverted link https://github.com/nodeca/pica (enable linkify to see)
                    """;
            String expectedHtmlText = """
                    <div class="lk-markdown-container"><hr>
                    <ul>
                    <li><strong><a href="https://nodeca.github.io/pica/demo/">pica</a></strong> - high quality and fast image<br>
                    resize in browser.</li>
                    <li><strong><a href="https://github.com/nodeca/babelfish/">babelfish</a></strong> - developer friendly<br>
                    i18n with plurals support and easy syntax.</li>
                    </ul>
                    <p>You will like those projects!</p>
                    <hr>
                    <h1>h1 Heading</h1>
                    <h2>h2 Heading</h2>
                    <h3>h3 Heading</h3>
                    <h4>h4 Heading</h4>
                    <h5>h5 Heading</h5>
                    <h6>h6 Heading</h6>
                    <h2>Horizontal Rules</h2>
                    <hr>
                    <hr>
                    <hr>
                    <h2>Typographic replacements</h2>
                    <p>Enable typographer option to see result.</p>
                    <p>(c) (C) (r) (R) (tm) (TM) (p) (P) +-</p>
                    <p>test.. test... test..... test?..... test!....</p>
                    <p>!!!!!! ???? ,,  -- ---</p>
                    <p>&quot;Smartypants, double quotes&quot; and 'single quotes'</p>
                    <h2>Emphasis</h2>
                    <p><strong>This is bold text</strong></p>
                    <p><strong>This is bold text</strong></p>
                    <p><em>This is italic text</em></p>
                    <p><em>This is italic text</em></p>
                    <p><s>Strikethrough</s></p>
                    <h2>Blockquotes</h2>
                    <blockquote>
                    <p>Blockquotes can also be nested...</p>
                    <blockquote>
                    <p>...by using additional greater-than signs right next to each other...</p>
                    <blockquote>
                    <p>...or with spaces between arrows.</p>
                    </blockquote>
                    </blockquote>
                    </blockquote>
                    <h2>Lists</h2>
                    <p>Unordered</p>
                    <ul>
                    <li>Create a list by starting a line with <code>+</code>, <code>-</code>, or <code>*</code></li>
                    <li>Sub-lists are made by indenting 2 spaces:
                    <ul>
                    <li>Marker character change forces new list start:
                    <ul>
                    <li>Ac tristique libero volutpat at</li>
                    </ul>
                    <ul>
                    <li>Facilisis in pretium nisl aliquet</li>
                    </ul>
                    <ul>
                    <li>Nulla volutpat aliquam velit</li>
                    </ul>
                    </li>
                    </ul>
                    </li>
                    <li>Very easy!</li>
                    </ul>
                    <p>Ordered</p>
                    <ol>
                    <li>
                    <p>Lorem ipsum dolor sit amet</p>
                    </li>
                    <li>
                    <p>Consectetur adipiscing elit</p>
                    </li>
                    <li>
                    <p>Integer molestie lorem at massa</p>
                    </li>
                    <li>
                    <p>You can use sequential numbers...</p>
                    </li>
                    <li>
                    <p>...or keep all the numbers as <code>1.</code></p>
                    </li>
                    </ol>
                    <p>Start numbering with offset:</p>
                    <ol start="57">
                    <li>foo</li>
                    <li>bar</li>
                    </ol>
                    <h2>Code</h2>
                    <p>Inline <code>code</code></p>
                    <p>Indented code</p>
                    <pre><code>// Some comments
                    line 1 of code
                    line 2 of code
                    line 3 of code
                    </code></pre>
                    <h2>Tables</h2>
                    <table>
                    <thead>
                    <tr>
                    <th>Option</th>
                    <th>Description</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr>
                    <td>data</td>
                    <td>path to data files to supply the data that will be passed into templates.</td>
                    </tr>
                    <tr>
                    <td>engine</td>
                    <td>engine to be used for processing templates. Handlebars is the default.</td>
                    </tr>
                    <tr>
                    <td>ext</td>
                    <td>extension to be used for dest files.</td>
                    </tr>
                    </tbody>
                    </table>
                    <p>Right aligned columns</p>
                    <table>
                    <thead>
                    <tr>
                    <th style="text-align:right">Option</th>
                    <th style="text-align:right">Description</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr>
                    <td style="text-align:right">data</td>
                    <td style="text-align:right">path to data files to supply the data that will be passed into templates.</td>
                    </tr>
                    <tr>
                    <td style="text-align:right">engine</td>
                    <td style="text-align:right">engine to be used for processing templates. Handlebars is the default.</td>
                    </tr>
                    <tr>
                    <td style="text-align:right">ext</td>
                    <td style="text-align:right">extension to be used for dest files.</td>
                    </tr>
                    </tbody>
                    </table>
                    <h2>Links</h2>
                    <p><a href="http://dev.nodeca.com">link text</a></p>
                    <p><a href="http://nodeca.github.io/pica/demo/" title="title text!">link with title</a></p>
                    <p>Autoconverted link <a href="https://github.com/nodeca/pica">https://github.com/nodeca/pica</a> (enable linkify to see)</p>
                    </div>""";
            String htmlText = markdownService.toHtml(testMdText);
            assertTrue("The MarkdownService failed to correctly translate complex markdown text to html.", expectedHtmlText.equals(htmlText));
        }
    }
}
