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

package org.labkey.api.security;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.AbstractSetValuedMap;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.util.PageFlowUtil;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * User: matthewb
 * Date: Feb 5, 2009
 * Time: 9:34:32 AM
  */
public class AuthenticatedResponse extends HttpServletResponseWrapper
{
    PrintWriter _safeWriter = null;

    public AuthenticatedResponse(HttpServletResponse response)
    {
        super(response);
    }


    @Override
    public PrintWriter getWriter() throws IOException
    {
        return super.getWriter();
//        // for debugging you can do this
//        if (_safeWriter == null)
//            _safeWriter = new SafePrintWriter(super.getWriter());
//        return _safeWriter;
    }

    @Override
    public void addHeader(String name, String value)
    {
        if (StringUtils.equalsIgnoreCase(name, "Content-Security-Policy"))
            throw new IllegalStateException("Use addContextSecurityPolicyHeader()");
        super.addHeader(name, value);
    }

    @Override
    public void setHeader(String name, String value)
    {
        if (StringUtils.equalsIgnoreCase(name, "Content-Security-Policy"))
            throw new IllegalStateException("Use setContextSecurityPolicyHeader()");
        super.setHeader(name, value);
    }

    public enum ContentSecurityPolicyEnum
    {
        ConnectSrc("connect-src"),
        DefaultSrc("default-src"),
        FontSrc("font-src"),
        FrameSrc("frame-src"),
        ImgSrc("img-src"),
        ManifestSrc("manifest-src"),
        ObjectSrc("object-src"),
        PrefetchSrc("prefetch-src"),
        ScriptSrc("script-src"),
        ScriptSrcAttr("script-src-attr"),
        StyleSrc("style-src"),
        StyleSrcElem("style-src-elem"),
        StyleSrcAttr("style-src-attr"),
        WorkerSrc("worker-src"),

        BaseURI("base-uri"),
        Sandbox("sandbox"),

        FormAction("form-action"),
        FrameAncestors("frame-ancestors"),
        NavigateTo("navigate-to"),

        ReportUri("report-uri"),
        ReportTo("report-to"),

        RequireSriFor("require-sri-for"),
        RequireTrustedTypesFor("require-trusted-types-for"),
        TrustedTypes("trusted-types"),

        UpgradeInsecureRequests("upgrade-insecure-requests")
        ;

        final String value;

        ContentSecurityPolicyEnum(String value)
        {
            this.value = value;
        }

        @Override
        public String toString()
        {
            return value;
        }
    }


    public enum CspSourceValues
    {
        Self("'self'"),
        None("'none'"),
        UnsafeEval("'unsafe-eval'"),
        UnsafeInline("'unsafe-inline'")
        ;

        final String value;

        CspSourceValues(String value)
        {
            this.value = value;
        }

        @Override
        public String toString()
        {
            return value;
        }
    }


    private final MultiValuedMap<String, String> _contentSecurityPolicy = new AbstractSetValuedMap<>(new TreeMap<>())
    {
        @Override
        protected Set<String> createCollection()
        {
            return new TreeSet<>();
        }
    };

    public void addContentSecurityPolicyHeader(ContentSecurityPolicyEnum csp, CspSourceValues value)
    {
        addContentSecurityPolicyHeader(csp, value.toString());
    }

    public void addContentSecurityPolicyHeader(ContentSecurityPolicyEnum csp, String... values)
    {
        // if this is a src header, copy the "defaults" from connect-src
        String target = csp.toString();
        if (target.endsWith("-src") && !target.equals(ContentSecurityPolicyEnum.DefaultSrc.toString()) && _contentSecurityPolicy.get(target).isEmpty())
            _contentSecurityPolicy.putAll(target, _contentSecurityPolicy.get(ContentSecurityPolicyEnum.DefaultSrc.toString()));
        _contentSecurityPolicy.putAll(target, Arrays.asList(values));
        _updateContentSecurityPolicyHeader();
    }

    public void setContentSecurityPolicyHeader(ContentSecurityPolicyEnum csp, CspSourceValues value)
    {
        setContentSecurityPolicyHeader(csp, value.toString());
    }

    public void setContentSecurityPolicyHeader(ContentSecurityPolicyEnum csp, String value)
    {
        _contentSecurityPolicy.remove(csp.toString());
        _contentSecurityPolicy.put(csp.toString(), value);
        _updateContentSecurityPolicyHeader();
    }

    private void _updateContentSecurityPolicyHeader()
    {
        StringBuilder content = new StringBuilder();
        var semi = "";
        for (var e : _contentSecurityPolicy.asMap().entrySet())
        {
            content.append(semi).append(e.getKey());
            semi = "; ";
            for (String s : e.getValue())
                content.append(" ").append(s);
        }
        super.setHeader("Content-Security-Policy", content.toString());
    }


    public static class SafePrintWriter extends PrintWriter
    {
        PrintWriter _writer;
        SafePrintWriter(PrintWriter writer)
        {
            super((Writer)null);
            _writer = writer;
        }

        @Override
        public void flush()
        {
            _writer.flush();
        }

        @Override
        public void close()
        {
            _writer.close();
        }

        @Override
        public boolean checkError()
        {
            return _writer.checkError();
        }

        @Override
        public void write(int c)
        {
            _writer.write(c);
        }

        @Override
        public void write(char[] buf, int off, int len)
        {
            _writer.write(buf, off, len);
        }

        @Override
        public void write(char[] buf)
        {
            _writer.write(buf);
        }

        @Override
        public void write(String s, int off, int len)
        {
            _writer.write(s, off, len);
        }

        @Override
        public void write(String s)
        {
            _writer.write(s);
        }

        @Override
        public void print(boolean b)
        {
            _writer.print(b);
        }

        @Override
        public void print(char c)
        {
            _writer.print(c);
        }

        @Override
        public void print(int i)
        {
            _writer.print(i);
        }

        @Override
        public void print(long l)
        {
            _writer.print(l);
        }

        @Override
        public void print(float f)
        {
            _writer.print(f);
        }

        @Override
        public void print(double d)
        {
            _writer.print(d);
        }

        @Override
        public void print(char[] s)
        {
            _writer.print(s);
        }

        @Override
        public void print(String s)
        {
            _writer.print(s);
        }

        @Override
        public void print(Object obj)
        {
            _writer.print(obj);
        }

        @Override
        public void println()
        {
            _writer.println();
        }

        @Override
        public void println(boolean x)
        {
            _writer.println(x);
        }

        @Override
        public void println(char x)
        {
            _writer.println(x);
        }

        @Override
        public void println(int x)
        {
            _writer.println(x);
        }

        @Override
        public void println(long x)
        {
            _writer.println(x);
        }

        @Override
        public void println(float x)
        {
            _writer.println(x);
        }

        @Override
        public void println(double x)
        {
            _writer.println(x);
        }

        @Override
        public void println(char[] x)
        {
            _writer.println(x);
        }

        @Override
        public void println(String x)
        {
            _writer.println(x);
        }

        @Override
        public void println(Object x)
        {
            _writer.println(x);
        }

        @Override
        public PrintWriter printf(String format, Object... args)
        {
            return format(format, args);
        }

        @Override
        public PrintWriter printf(Locale l, String format, Object... args)
        {
            return format(l, format, args);
        }

        @Override
        public PrintWriter format(String format, Object... args)
        {
            return _writer.printf(PageFlowUtil.filter(String.format(format,args)));
        }

        @Override
        public PrintWriter format(Locale l, String format, Object... args)
        {
            return _writer.printf(PageFlowUtil.filter(String.format(l, format, args)));
        }

        @Override
        public PrintWriter append(CharSequence csq)
        {
            return _writer.append(csq);
        }

        @Override
        public PrintWriter append(CharSequence csq, int start, int end)
        {
            return _writer.append(csq, start, end);
        }

        @Override
        public PrintWriter append(char c)
        {
            return _writer.append(c);
        }
    }
}
