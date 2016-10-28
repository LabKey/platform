/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

package org.labkey.api.util;

import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTML;
import javax.swing.text.html.parser.ParserDelegator;
import javax.swing.text.MutableAttributeSet;
import java.io.Reader;
import java.io.StringReader;
import java.io.IOException;

public abstract class HTMLContentExtractor extends HTMLEditorKit.ParserCallback
{
    private StringBuilder _text = new StringBuilder();
    private StringBuilder _title = new StringBuilder();
    private Reader _reader;
    protected boolean _isBody = false;
    protected boolean _isTitle = false;

    public HTMLContentExtractor(String html)
    {
        _reader = new StringReader(html);
    }

    public HTMLContentExtractor(Reader reader)
    {
        _reader = reader;
    }

    public String extract() throws IOException
    {
        ParserDelegator parserDelegator = new ParserDelegator();
        parserDelegator.parse(_reader, this, true);
        _reader.close();
        return _text.toString();
    }

    @Override
    public void handleText(char[] data, int pos)
    {
        if (_isBody)
        {
            _text.append(data);
            _text.append("\n");
        }
        if (_isTitle)
            _title.append(data);
    }

    @Override
    public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos)
    {
        if (HTML.Tag.TITLE == t)
            _isTitle = true;

        super.handleStartTag(t, a, pos);
    }

    @Override
    public void handleEndTag(HTML.Tag t, int pos)
    {
        if (HTML.Tag.TITLE == t)
            _isTitle = false;

        super.handleEndTag(t, pos);
    }

    public String getTitle()
    {
        if (_title.length() == 0)
            return null;
        else
            return _title.toString();
    }

    // Extract readable text between <body> </body>
    public static class GenericHTMLExtractor extends HTMLContentExtractor
    {
        public GenericHTMLExtractor(String html)
        {
            super(html);
        }

        @Override
        public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos)
        {
            if (HTML.Tag.BODY == t)
                _isBody = true;

            super.handleStartTag(t, a, pos);
        }

        @Override
        public void handleEndTag(HTML.Tag t, int pos)
        {
            if (HTML.Tag.BODY == t)
                _isBody = false;

            super.handleEndTag(t, pos);
        }
    }
}
