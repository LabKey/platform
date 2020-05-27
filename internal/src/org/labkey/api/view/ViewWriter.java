/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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

import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.PageContext;
import java.io.IOException;
import java.io.PrintWriter;


public class ViewWriter extends PrintWriter
{
    private PrintWriter _out;

    public ViewWriter(PageContext pageContext) throws IOException
    {
        super((PrintWriter) null);
        HttpServletResponse response = (HttpServletResponse) pageContext.getResponse();
        _out = response.getWriter();
        // UNDONE: getContentType? assume text/html;charset=UTF-8
    }

    @Override
    public void close()
    {
        _out.close();
    }

    @Override
    public void flush()
    {
        _out.flush();
    }

    @Override
    public void print(boolean b)
    {
        _out.print(b);
    }

    @Override
    public void print(char c)
    {
        _out.print(c);
    }

    @Override
    public void print(double d)
    {
        _out.print(d);
    }

    @Override
    public void print(int i)
    {
        _out.print(i);
    }

    @Override
    public void print(long l)
    {
        _out.print(l);
    }

    @Override
    public void print(java.lang.String s)
    {
        _out.print(s);
    }

    @Override
    public void println()
    {
        _out.println();
    }

    @Override
    public void println(boolean b)
    {
        _out.println(b);
    }

    @Override
    public void println(char c)
    {
        _out.println(c);
    }

    @Override
    public void println(double d)
    {
        _out.println(d);
    }

    @Override
    public void println(float f)
    {
        _out.println(f);
    }

    @Override
    public void println(int i)
    {
        _out.println(i);
    }

    @Override
    public void println(long l)
    {
        _out.println(l);
    }

    @Override
    public void println(java.lang.String s)
    {
        _out.println(s);
    }

    /**
     * assumes _out.print(String s) is fast (the ServletOutputStream
     * abstract class implementation is not!).
     * <p/>
     * assumes s.substring() is fast (it is)
     */

    //static byte[]  bytesLT = new byte[]   {'&', 'l', 't', ';'};
    //static byte[]  bytesGT = new byte[]   {'&', 'g', 't', ';'};
    //static byte[]  bytesQUOT = new byte[] {'&', 'q', 'u', 'o', 't', ';'};
    //static byte[]  bytesAMP = new byte[]  {'&', 'a', 'm', 'p', ';'};

    String strLT = "&lt;";
    String strGT = "&gt;";
    String strQUOT = "&quot;";
    String strAMP = "&amp;";

    // output plain text into html stream
    public void printText(String s)
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
                    _out.write(strLT);
                    break;
                case '>':
                    //_out.print("&gt;");
                    _out.write(strGT);
                    break;
                case '&':
                    //_out.print("&amp;");
                    _out.write(strAMP);
                    break;
                case '"':
                    //_out.print("&quot;");
                    _out.write(strQUOT);
                    break;
                default:
                    assert null == "bug in ViewOutputStream.printText(String s)";
                    break;
            }
            pos = next;
        }
    }
	}