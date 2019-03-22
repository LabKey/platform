/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
import javax.swing.text.html.parser.ParserDelegator;
import java.io.Reader;
import java.io.StringReader;
import java.io.IOException;

/**
 * User: adam
 * Date: Nov 29, 2008
 * Time: 12:02:50 PM
 */

// Extracts just the readable text from html source
public class TextExtractor extends HTMLEditorKit.ParserCallback
{
    private StringBuffer _text;
    private Reader _reader;

    public TextExtractor(String html)
    {
        _reader = new StringReader(html);
    }

    public TextExtractor(Reader reader)
    {
        _reader = reader;
    }

    public String extract() throws IOException
    {
        _text = new StringBuffer();
        ParserDelegator parserDelegator = new ParserDelegator();
        parserDelegator.parse(_reader, this, true);
        _reader.close();
        return _text.toString();
    }

    @Override
    public void handleText(char[] data, int pos)
    {
        _text.append(data);
        _text.append("\n");
    }
}
