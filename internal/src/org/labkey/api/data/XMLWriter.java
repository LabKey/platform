/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.api.data;

import org.apache.commons.collections15.ArrayStack;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.util.PageFlowUtil;

import java.io.PrintWriter;

/**
 * User: adam
 * Date: Mar 14, 2008
 * Time: 5:56:08 PM
 */
public class XMLWriter
{
    private PrintWriter _out;
    private ArrayStack<Tag> _stack = new ArrayStack<Tag>();
    private StringBuilder _currentLine = new StringBuilder();
    private int _currentIndent = 0;
    private int _indentSpacing = 2;
    private int _maxWidth = 80;
    private boolean _isStartTag = false;      // Are we in the midst of a start tag?
    private int _currentAttributeCount;

    public XMLWriter(PrintWriter out)
    {
        _out = out;
    }

    private void push(Tag tag)
    {
        _currentIndent = _stack.size() * _indentSpacing;
        _stack.push(tag);
    }

    private Tag pop()
    {
        Tag tag = _stack.pop();
        _currentIndent = _stack.size() * _indentSpacing;
        return tag;
    }

    public void startTag(Tag tag, String... attributes)
    {
        closePreviousStartTag(true);
        _isStartTag = true;
        _currentAttributeCount = 0;
        push(tag);
        print("<");
        print(tag.name());
        attributes(attributes);
    }

    private void closePreviousStartTag(boolean forceNewLine)
    {
        if (_isStartTag)
        {
            print(">", forceNewLine);
            verifyAttributeCount(_stack.peek());
        }

        _isStartTag = false;
    }

    private void verifyAttributeCount(Tag tag)
    {
        if (_currentAttributeCount < tag.getMinimumAttributeCount())
            throw new IllegalStateException("Too few attributes were passed to tag " + tag.name());

        if (_currentAttributeCount > tag.getMaximumAttributeCount())
            throw new IllegalStateException("Too many attributes were passed to tag " + tag.name());

        _currentAttributeCount = 0;
    }

    public void attributes(String... attributes)
    {
        if (0 != attributes.length % 2)
            throw new IllegalStateException("Odd number of attribute parameters on tag " + _stack.peek());

        for (int i = 0; i < attributes.length; i += 2)
            attribute(attributes[i], attributes[i + 1]);
    }

    public void value(String s)
    {
        closePreviousStartTag(false);
        print(PageFlowUtil.filterXML(s));
    }

    public void attribute(String name, String value)
    {
        String attribute = " " + name + "=\"" + value + "\"";
        print(attribute);
        _currentAttributeCount++;
    }

    public void endTag()
    {
        Tag tag = pop();

        if (_isStartTag)
        {
            println(" />");
            verifyAttributeCount(tag);
        }
        else
        {
            println("</" + tag.name() + ">");
        }

        _isStartTag = false;
    }

    private void println(String s)
    {
        print(s, true);
    }

    private void print(String s)
    {
        print(s, false);
    }

    private void print(String s, boolean forceNewLineAfter)
    {
        if (_currentLine.length() + s.length() + _currentIndent > _maxWidth)
            outputLine();

        _currentLine.append(s);

        if (forceNewLineAfter)
            outputLine();
    }

    private void outputLine()
    {
        _out.print(StringUtils.repeat(" ", _currentIndent));
        _out.println(_currentLine);
        _currentLine = new StringBuilder();
    }

    public void close()
    {
        if (_currentLine.length() > 0)
            outputLine();

        _out.flush();
        _out.close();

        if (!_stack.empty())
            throw new IllegalStateException("Unclosed tags: " + _stack.toString());
    }

    public interface Tag
    {
        public String name();
        public int getMinimumAttributeCount();
        public int getMaximumAttributeCount();
    }
}
