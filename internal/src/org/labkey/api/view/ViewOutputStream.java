/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.view;

import java.io.IOException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.PageContext;


public class ViewOutputStream extends ServletOutputStream
{
    private ServletOutputStream _out;

    public ViewOutputStream(PageContext pageContext) throws IOException
    {
        HttpServletResponse response = (HttpServletResponse) pageContext.getResponse();
        _out = response.getOutputStream();
        // UNDONE: getContentType? assume text/html;charset=UTF-8
    }

    public void close() throws IOException
    {
        _out.close();
    }

    public void flush() throws IOException
    {
        _out.flush();
    }

    public void write(byte[] b) throws IOException
    {
        _out.write(b);
    }

    public void write(byte[] b, int off, int len) throws IOException
    {
        _out.write(b, off, len);
    }

    public void write(int b) throws IOException
    {
        _out.write(b);
    }

    public void print(boolean b) throws IOException
    {
        _out.print(b);
    }

    public void print(char c) throws IOException
    {
        _out.print(c);
    }

    public void print(double d) throws IOException
    {
        _out.print(d);
    }

    public void print(float f) throws IOException
    {
        _out.print(f);
    }

    public void print(int i) throws IOException
    {
        _out.print(i);
    }

    public void print(long l) throws IOException
    {
        _out.print(l);
    }

    public void print(java.lang.String s) throws IOException
    {
        _out.print(s);
    }

    public void println() throws IOException
    {
        _out.println();
    }

    public void println(boolean b) throws IOException
    {
        _out.println(b);
    }

    public void println(char c) throws IOException
    {
        _out.println(c);
    }

    public void println(double d) throws IOException
    {
        _out.println(d);
    }

    public void println(float f) throws IOException
    {
        _out.println(f);
    }

    public void println(int i) throws IOException
    {
        _out.println(i);
    }

    public void println(long l) throws IOException
    {
        _out.println(l);
    }

    public void println(java.lang.String s) throws IOException
    {
        _out.println(s);
    }

    /**
     * assumes _out.print(String s) is fast (the ServletOutputStream
     * abstract class implementation is not!).
     * <p/>
     * assumes s.substring() is fast (it is)
     */

    static byte[] bytesLT = new byte[]{'&', 'l', 't', ';'};
    static byte[] bytesGT = new byte[]{'&', 'g', 't', ';'};
    static byte[] bytesQUOT = new byte[]{'&', 'q', 'u', 'o', 't', ';'};
    static byte[] bytesAMP = new byte[]{'&', 'a', 'm', 'p', ';'};


    // output plain text into html stream
    public void printText(String s) throws IOException
    {
        if (null == s) return;

        int len = s.length();
        int pos = 0;
        int next = 0;
        char ch = 0;

        while (true)
        {
            for (next = pos; next < len; next++)
            {
                ch = s.charAt(next);
                if (ch == '<' || ch == '>' || ch == '&' || ch == '"')
                    break;
            }
            _out.print(s.substring(pos, next));
            if (next == len)
                break;
            switch (ch)
            {
                case '<':
                    //_out.print("&lt;");
                    _out.write(bytesLT);
                    break;
                case '>':
                    //_out.print("&gt;");
                    _out.write(bytesGT);
                    break;
                case '&':
                    //_out.print("&amp;");
                    _out.write(bytesAMP);
                    break;
                case '"':
                    //_out.print("&quot;");
                    _out.write(bytesQUOT);
                    break;
                default:
                    assert null == "bug in ViewOutputStream.printText(String s)";
                    break;
            }
            pos = next;
        }
    }
	}