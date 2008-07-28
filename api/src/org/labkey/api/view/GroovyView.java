/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;
import groovy.lang.Script;
import org.apache.log4j.Logger;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.syntax.SyntaxException;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.CacheMap;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.common.util.Pair;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import javax.servlet.ServletException;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * User: mbellew
 * Date: May 10, 2004
 * Time: 3:37:42 PM
 * <p/>
 * This simply wraps a groovy script which is used a the render() method.
 * It would also be possible to load a Groovy class which directly extends View.
 * That's not this class, however.
 */
public class GroovyView<ModelClass> extends WebPartView<ModelClass>
{
    private static Logger _log = Logger.getLogger(GroovyView.class);
    private static final Set<String> _renderedTemplates = new HashSet<String>(110);         // Used to track the templates we've successfully rendered; see issue #2651.

    private static final CacheMap<String, Pair<Long, Class<GroovyObject>>> _groovyClasses = new CacheMap<String, Pair<Long, Class<GroovyObject>>>(20);

    String _templateName = null;
    GroovyObject _groovyObject = null;
    Message _message = null;
    Errors _errors = null;


    public GroovyView(String file)
    {
        this(file, (ModelClass)null, null);
    }


    public GroovyView(String file, String title)
    {
        this(file, title, null);
    }

    public GroovyView(String file, String title, Errors errors)
    {
        this(file, (ModelClass)null, errors);
        setTitle(title);
    }


    public GroovyView(String file, ModelClass model)
    {
        this(file, model, null);
    }

    public GroovyView(String file, ModelClass model, Errors errors)
    {
        super(model);
        _errors = errors;

        assert MemTracker.put(this);
        _templateName = file;

        try
        {
            Class clss = getGroovyClass(file);
            _groovyObject = (GroovyObject) clss.newInstance();
        }
        catch (SyntaxException x)
        {
            throw new RuntimeException(x.getMessage(), x);
        }
        catch (CompilationFailedException x)
        {
            if (x instanceof MultipleCompilationErrorsException)
                _message = ((MultipleCompilationErrorsException) x).getErrorCollector().getError(0);
            else
                throw new RuntimeException(x.getMessage(), x);
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
        catch (IllegalAccessException x)
        {
            throw new RuntimeException(x);
        }
        catch (InstantiationException x)
        {
            throw new RuntimeException(x);
        }
    }


    @Override
    public String toString()
    {
        return "GrooyView: " + _templateName;
    }

    public static Class<GroovyObject> getGroovyClass(String file) throws
            SyntaxException, CompilationFailedException, IOException
    {
        if (!file.startsWith("/") && !file.startsWith("\\"))
            file = "/" + file;

        synchronized (_groovyClasses)
        {
            Pair<Long, Class<GroovyObject>> pair = _groovyClasses.get(file);

            Pair<Long,String> readScript = null;
            if (null == pair)
                readScript = _readScript(file, -1L);    // not cached... force read
            else if (AppProps.getInstance().isDevMode())
                readScript = _readScript(file, pair.first); // in devmode, reload if the ts has changed

            assert null != pair || null != readScript;
            if (null != readScript)
            {
                String script = readScript.second;
                if (file.endsWith(".gm"))
                    script = _translateScript(file, script);

                GroovyClassLoader loader = new GroovyClassLoader(GroovyView.class.getClassLoader());
                Class<GroovyObject> c = loader.parseClass(script);
                pair = new Pair<Long,Class<GroovyObject>>(readScript.first, c);
                _groovyClasses.put(file, pair);
            }
            return pair.second;
        } // synchronized
    }


    // Load script file from either the file system (dev mode) or resourceloader (production mode)
    // Timestamp is ignored in production mode -- we assume scripts won't be changing out from under us in production
    private static Pair<Long,String> _readScript(String path, long tsPrevious) throws IOException
    {
        InputStream is = null;
        Module module = ModuleLoader.getInstance().getModuleForResourcePath(path);

        try
        {
            Pair<InputStream, Long> pair = module.getResourceStreamIfChanged(path, tsPrevious);

            if (null == pair)
                return null;

            is = pair.first;
            long ts = pair.second;

            BufferedReader r = new BufferedReader(new java.io.InputStreamReader(is));
            StringBuffer sb = new StringBuffer();
            String t;
            while (null != (t = r.readLine()))    // MAB: why were we using a BufferedReader again?
            {
                sb.append(t);
                sb.append('\n');
            }

            String scriptFile = sb.toString();

            return new Pair<Long,String>(ts, scriptFile);
        }
        finally
        {
            if (null != is)
            {
                try
                {
                    is.close();
                }
                catch (Exception x)
                {
                    _log.error("Error closing stream");
                }
            }
        }
    }

    static Pattern scriptPattern = Pattern.compile("<%--(.*?)--%>|<%(.*?)%>", Pattern.DOTALL);
    static Pattern importOrExtendsPattern = Pattern.compile("@\\s*page\\s*(import|extends)=\\\"([\\.\\w]*)\\\"");
    static Pattern hashPattern = Pattern.compile("^#(.*)$", Pattern.UNIX_LINES);

    static String _translateScript(String filename, String scriptFile) throws SyntaxException
    {
        /*
        OK I can't figure out the pattern to make this work see version below
        // translate #if #else #end
        Matcher m1 = hashPattern.matcher(scriptFile);
        StringBuffer sb1 = new StringBuffer(scriptFile.length() + 100);

        while (m1.find())
            {
            m1.appendReplacement(sb1,"");
            String line = m1.group(1);
            if (line.startsWith("if"))
                sb1.append(line).append("{%>");
            else if (line.startsWith("else"))
                sb1.append("<%}else{%>");
            else if (line.startsWith("end"))
                sb1.append("");
            else if (line.startsWith("##"))
                ;
            else
                sb1.append(m1.group(0));
            }
        m1.appendTail(sb1);
        */

        // translate #if #else #end
        StringBuffer sb1 = new StringBuffer(scriptFile.length() + 200);

        int endPrev = 0;    // end of previous match
        int start = -1;  // start of next match
        while (-1 != (start = scriptFile.indexOf('#', start + 1)))
        {
            if (start > 0 && scriptFile.charAt(start - 1) != '\n')
                continue;
            sb1.append(scriptFile.substring(endPrev, start));
            int end = scriptFile.indexOf('\n', start + 1);
            if (-1 == end)
                end = scriptFile.length();
            String line = scriptFile.substring(start + 1, end).trim();
            if (line.startsWith("if"))
                sb1.append("<%").append(line).append("{\n%>");
            else if (line.startsWith("else"))
                sb1.append("<%}").append(line).append("{\n%>");
            else if (line.startsWith("end"))
                sb1.append("<%}\n%>");
            else if (line.startsWith("#"))
                sb1.append("<%//").append(line.substring(1)).append("\n%>");
            else
                sb1.append('#').append(line).append('\n'); // leave it be
            start = end;
            endPrev = end + 1; // skip newline
        }
        sb1.append(scriptFile.substring(endPrev));

        String template = sb1.toString();
        sb1 = null;

        //
        // translate <% %>
        //
        Matcher m2 = scriptPattern.matcher(template);
        StringBuffer script = new StringBuffer(template.length() * 2 + 200);
        appendImports(script);

        for (endPrev = 0; m2.find(); endPrev = m2.end())
        {
            start = m2.start();
            String text = start > endPrev ? template.substring(endPrev, start) : "";
            if (0 != text.length())
                appendPrintText(script, text);
            // skip comment pattern
            if (m2.group(1) != null)
                continue;
            String code = m2.group(2);
            // handle <%@ page import="org.labkey.api.util.PageFlowUtil"%>
            // just to make using jsp editor kinda work
            if (code.startsWith("@"))
            {
                Matcher m = importOrExtendsPattern.matcher(code);
                if (!m.find())
                    throw new SyntaxException(code, 0, 0);
                String directive = m.group(1);
                String clazz = m.group(2);
                if ("import".equals(directive))
                    script.append("import ").append(clazz).append("; ");
                // ignore "extends" for now
            }
/*
            else if (code.startsWith("=&"))
                {
                sb2.append(";print(filter(").append(code.substring(2)).append("));");
                }
*/
            else if (code.startsWith("="))
            {
                script.append(";safe_print(").append(code.substring(1)).append(");");
            }
            else if (code.startsWith("--"))
            {
                // ignore comments
            }
            else
            {
                script.append(";");
                script.append(code);
            }
        } // for
        start = template.length();
        if (start > endPrev + 1 || 0 != template.substring(endPrev, start).trim().length())
        {
            String str = template.substring(endPrev, start);
            appendPrintText(script, str);
        }
        appendFunctions(script);

        String translated = script.toString();
        script = null;

        if (_log.isDebugEnabled())
        {
            StringBuffer sbScript = new StringBuffer();
            sbScript.append(filename).append("\n");
            String lines[] = translated.split("\n");
            for (int i = 0; i < lines.length; i++)
                sbScript.append("").append(i + 1).append(": ").append(lines[i]).append("\n");
            _log.debug(sbScript.toString());
        }

        return translated;
    }


    private static void appendPrintText(StringBuffer sb2, String str)
    {
        if (str.length() == 0)
            return;
        str = valueOfSubst(str);
        str = str.replaceAll("\\\\", "\\\\\\\\");
//        str = str.replaceAll("\"", "\\\\\"");
        sb2.append(";out.print(\"\"\"");
        if (str.charAt(str.length() - 1) == '"')
            sb2.append(str.substring(0, str.length() - 1)).append("\\\"");
        else
            sb2.append(str);
        sb2.append("\"\"\");");
    }


    private static void appendImports(StringBuffer sb)
    {
        sb.append("import java.util.*; import org.labkey.api.util.PageFlowUtil;");
    }


    private static void appendFunctions(StringBuffer sb)
    {
        sb.append("\n");
        sb.append("def getViewContext() { return context;}\n");
        sb.append("def getModelBean() { return modelBean;}\n");
        sb.append("def safe_print(o)  { if (null == o) return; out.print(o); }\n");
        //sb.append("def printf(String fmt, java.util.List args) { out.printf(fmt, args.toArray()); }\n");
        sb.append("def filter(o) { return PageFlowUtil.filter(o);}\n");
        sb.append("def h(o) { return PageFlowUtil.filter(o);}\n");
        sb.append("def toString(o) { if (null == o) return ''; return String.valueOf(o); }\n");
        sb.append("def include(view, out) { currentView.include(view, out); }\n");
    }


    public static Pattern subst = Pattern.compile("\\$\\{(.*?)\\}");

    protected static String valueOfSubst(String str)
    {
        if (null == str || 0 == str.length()) return "";
        StringBuffer sb = new StringBuffer(str.length() + 32);
        Matcher m = subst.matcher(str);
        while (m.find())
            m.appendReplacement(sb, "\\${toString($1)}");
        m.appendTail(sb);
        return sb.toString();
    }

    /*
    protected static final String convertSubst(String str)
        {
        if (null == str || 0 == str.length()) return "";
        StringBuffer sb = new StringBuffer(str.length() + 32);
        Matcher m = subst.matcher(str);
        while (m.find())
            m.appendReplacement(sb, "<%=$1%>");
        m.appendTail(sb);
        return sb.toString();
        }
    */


    public static String valueOf(Object o)
    {
        if (null == o)
            return "";
        return String.valueOf(o);
    }


    @Override
    protected void prepareWebPart(ModelClass model) throws ServletException
    {
        //
        // CONSIDER: look for prepare method in script and call it
        //
        super.prepareWebPart(model);
    }


    @Override
    public void renderView(ModelClass modelBean, PrintWriter out) throws Exception
    {
        if (null != _message)
        {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            _message.write(pw);
            pw.write(" while compiling ");
            pw.write(_templateName);
            String message = sw.toString();
            pw.close();
            sw.close();

            throw new ServletException(message);
        }

        try
        {
            Binding binding = createModelBinding(getViewContext(), true);
            binding.setVariable("out", out);
            binding.setVariable("context", getViewContext());
            binding.setVariable("currentContext", getViewContext());
            binding.setVariable("currentView", this);
            binding.setVariable("modelBean", modelBean);

            ((Script) _groovyObject).setBinding(binding);
            ((Script) _groovyObject).run();

            addTemplate(_templateName);
        }
        catch (Exception e)
        {
            Throwable x = e;
            if (x.getClass().equals(RuntimeException.class))
                x = x.getCause();
            if (x instanceof InvocationTargetException)
                x = x.getCause();
            throw new ServletException(x.getMessage() + " while rendering " + _templateName, x);  // Add template name to exception
        }
    }


    public static Set<String> getRenderedTemplates()
    {
        synchronized(_renderedTemplates)
        {
            return new HashSet<String>(_renderedTemplates);
        }
    }


    private void addTemplate(String template)
    {
        synchronized(_renderedTemplates)
        {
            _renderedTemplates.add(template);
        }
    }


    public static Binding createModelBinding(Map model, boolean copyBinding)
    {
        if (!copyBinding)
            return new Binding(model);

        HashMap<Object, Object> copy = new HashMap<Object, Object>();
        for (Object key : model.keySet()) // faster than entrySet for BoundMap
        {
            copy.put(key, model.get(key));
        }
        return new Binding(copy);
    }

    protected String renderErrors(boolean fieldNames)
    {
        if (_errors == null || !_errors.hasErrors())
            return "";
        List<ObjectError> l = (List<ObjectError>)_errors.getAllErrors();
        ViewContext context = getViewContext();
        StringBuffer message = new StringBuffer();
        String br = "";
        message.append("<span style=\"color:red;\">");
        for (ObjectError e : l)
        {
            message.append(br);
            br = "<br>";
            if (fieldNames && e instanceof FieldError)
            {
                message.append("<b>" + PageFlowUtil.filter(((FieldError)e).getField()) + ":</b>&nbsp;");
            }
            message.append(PageFlowUtil.filter(context.getMessage(e)));
        }
        message.append("</span>");
        return message.toString();
    }
}
