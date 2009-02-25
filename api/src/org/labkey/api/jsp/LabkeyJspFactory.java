/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.jsp;

import org.labkey.api.util.HString;
import org.labkey.api.util.Taintable;
import static org.labkey.api.util.PageFlowUtil.filter;

import javax.servlet.jsp.*;
import javax.servlet.jsp.el.ExpressionEvaluator;
import javax.servlet.jsp.el.VariableResolver;
import javax.servlet.*;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.Writer;
import java.util.Enumeration;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Feb 10, 2009
 * Time: 1:02:46 PM
 */
public class LabkeyJspFactory extends JspFactory
{
    final JspFactory _f;

    public LabkeyJspFactory(JspFactory f)
    {
        _f = f;
    }

    public PageContext getPageContext(Servlet servlet, ServletRequest servletRequest, ServletResponse servletResponse, String s, boolean b, int i, boolean b1)
    {
        PageContext p = _f.getPageContext(servlet, servletRequest, servletResponse, s, b, i, b1);
        return new _PageContext(p);
    }

    public void releasePageContext(PageContext pageContext)
    {
        _f.releasePageContext(pageContext);
    }

    public JspEngineInfo getEngineInfo()
    {
        return _f.getEngineInfo();
    }


    class _PageContext extends PageContext
    {
        final PageContext _p;
        JspWriter _out = null;

        _PageContext(PageContext p)
        {
            _p = p;
        }

        public void initialize(Servlet servlet, ServletRequest servletRequest, ServletResponse servletResponse, String s, boolean b, int i, boolean b1) throws IOException, IllegalStateException, IllegalArgumentException
        {
            _p.initialize(servlet,  servletRequest, servletResponse, s, b, i, b1);
        }

        public void release()
        {
            _p.release();
        }

        public HttpSession getSession()
        {
            return _p.getSession();
        }

        public Object getPage()
        {
            return _p.getPage();
        }

        public ServletRequest getRequest()
        {
            return _p.getRequest();
        }

        public ServletResponse getResponse()
        {
            return _p.getResponse();
        }

        public Exception getException()
        {
            return _p.getException();
        }

        public ServletConfig getServletConfig()
        {
            return _p.getServletConfig();
        }

        public ServletContext getServletContext()
        {
            return _p.getServletContext();
        }

        public void forward(String s) throws ServletException, IOException
        {
            _p.forward(s);
        }

        public void include(String s) throws ServletException, IOException
        {
            _p.include(s);
        }

        public void include(String s, boolean b) throws ServletException, IOException
        {
            _p.include(s,b);
        }

        public void handlePageException(Exception e) throws ServletException, IOException
        {
            _p.handlePageException(e);
        }

        public void handlePageException(Throwable throwable) throws ServletException, IOException
        {
            _p.handlePageException(throwable);
        }

        public void setAttribute(String s, Object o)
        {
            _p.setAttribute(s,o);
        }

        public void setAttribute(String s, Object o, int i)
        {
            _p.setAttribute(s,o,i);
        }

        public Object getAttribute(String s)
        {
            return _p.getAttribute(s);
        }

        public Object getAttribute(String s, int i)
        {
            return _p.getAttribute(s,i);
        }

        public Object findAttribute(String s)
        {
            return _p.findAttribute(s);
        }

        public void removeAttribute(String s)
        {
            _p.removeAttribute(s);
        }

        public void removeAttribute(String s, int i)
        {
            _p.removeAttribute(s,i);
        }

        public int getAttributesScope(String s)
        {
            return _p.getAttributesScope(s);
        }

        public Enumeration getAttributeNamesInScope(int i)
        {
            return _p.getAttributeNamesInScope(i);
        }

        public JspWriter getOut()
        {
            if (null == _out)
                _out = new _JspWriter(_p.getOut());
            return _out;
        }

        public ExpressionEvaluator getExpressionEvaluator()
        {
            return _p.getExpressionEvaluator();
        }

        public VariableResolver getVariableResolver()
        {
            return _p.getVariableResolver();
        }
    }

    class _JspWriter extends JspWriter
    {
        private final JspWriter _out;

        _JspWriter(JspWriter out)
        {
            super(0,false);
            _out = out;
        }

        public void newLine()
                throws IOException
        {
            _out.newLine();
        }

        public void print(boolean b)
                throws IOException
        {
            _out.print(b);
        }

        public void print(char c)
                throws IOException
        {
            _out.print(c);
        }

        public void print(int i)
                throws IOException
        {
            _out.print(i);
        }

        public void print(long l)
                throws IOException
        {
            _out.print(l);
        }

        public void print(float v)
                throws IOException
        {
            _out.print(v);
        }

        public void print(double v)
                throws IOException
        {
            _out.print(v);
        }

        public void print(char[] chars)
                throws IOException
        {
            _out.print(chars);
        }

        public void print(String s)
                throws IOException
        {
            _out.print(s);
        }

        public void print(Object o)
                throws IOException
        {
            if (o instanceof HString)
                _out.print(filter((HString)o));
            else
                _out.print(o);
        }

        public void println()
                throws IOException
        {
            _out.println();
        }

        public void println(boolean b)
                throws IOException
        {
            _out.println(b);
        }

        public void println(char c)
                throws IOException
        {
            _out.println(c);
        }

        public void println(int i)
                throws IOException
        {
            _out.println(i);
        }

        public void println(long l)
                throws IOException
        {
            _out.println(l);
        }

        public void println(float v)
                throws IOException
        {
            _out.println(v);
        }

        public void println(double v)
                throws IOException
        {
            _out.println(v);
        }

        public void println(char[] chars)
                throws IOException
        {
            _out.println(chars);
        }

        public void println(String s)
                throws IOException
        {
            _out.println(s);
        }

        public void println(Object o)
                throws IOException
        {
            if (o instanceof Taintable && ((Taintable)o).isTainted())
                _out.println(filter((HString)o));
            else
                _out.println(o);
        }

        public void clear()
                throws IOException
        {
            _out.clear();
        }

        public void clearBuffer()
                throws IOException
        {
            _out.clearBuffer();
        }

        public void flush()
                throws IOException
        {
            _out.flush();
        }

        public void close()
                throws IOException
        {
            _out.close();
        }

        public int getRemaining()
        {
            return _out.getRemaining();
        }

        public void write(char[] cbuf, int off, int len)
                throws IOException
        {
            _out.write(cbuf, off, len);
        }

        @Override
        public int getBufferSize()
        {
            return _out.getBufferSize();
        }

        @Override
        public boolean isAutoFlush()
        {
            return _out.isAutoFlush();
        }

        @Override
        public void write(int c) throws IOException
        {
            _out.write(c);
        }

        @Override
        public void write(char[] cbuf) throws IOException
        {
            _out.write(cbuf);
        }

        @Override
        public void write(String str) throws IOException
        {
            _out.write(str);
        }

        @Override
        public void write(String str, int off, int len) throws IOException
        {
            _out.write(str, off, len);
        }

        @Override
        public Writer append(CharSequence csq) throws IOException
        {
            if (csq instanceof Taintable && ((Taintable)csq).isTainted())
                _out.append(filter((HString)csq));
            else
                _out.append(csq);
            return this;
        }

        @Override
        public Writer append(CharSequence csq, int start, int end) throws IOException
        {
            if (csq instanceof Taintable && ((Taintable)csq).isTainted())
                _out.append(filter((HString)csq));
            else
                _out.append(csq, start, end);
            return this;
        }

        @Override
        public Writer append(char c) throws IOException
        {
            return _out.append(c);
        }
    }
}
