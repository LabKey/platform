/*
 * Copyright (c) 2004-2007 Fred Hutchinson Cancer Research Center
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

package org.labkey.core.junit;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestFailure;
import junit.framework.TestResult;
import org.apache.commons.lang.time.FastDateFormat;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.Format;
import java.util.*;


public class JunitController extends SpringActionController
{
    private static ActionResolver _resolver = new DefaultActionResolver(JunitController.class);
    private static Map<String, List<Class<? extends TestCase>>> _testCases;


    public JunitController()
    {
        super();
        setActionResolver(_resolver);
    }

    private Map<String, List<Class<? extends TestCase>>> getTestCases()
    {
        synchronized(JunitController.class)
        {
            Set<Class<? extends TestCase>> allCases = new HashSet<Class<? extends TestCase>>();

            if (_testCases == null)
            {
                _testCases = new TreeMap<String, List<Class<? extends TestCase>>>();

                for (Module module : ModuleLoader.getInstance().getModules())
                {
                    Set<Class<? extends TestCase>> clazzes = module.getJUnitTests();
                    List<Class<? extends TestCase>> moduleClazzes = new ArrayList<Class<? extends TestCase>>();

                    for (Class<? extends TestCase> clazz : clazzes)
                    {
                        if (allCases.contains(clazz))
                            continue;

                        allCases.add(clazz);
                        moduleClazzes.add(clazz);
                    }

                    if (!moduleClazzes.isEmpty())
                    {
                        Collections.sort(moduleClazzes, new Comparator<Class<? extends TestCase>>(){
                            public int compare(Class<? extends TestCase> c1, Class<? extends TestCase> c2)
                            {
                                return c1.getName().compareTo(c2.getName());
                            }
                        });
                        _testCases.put(module.getName(), moduleClazzes);
                    }
                }
            }
            return _testCases;
        }
    }


    @RequiresSiteAdmin
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            HttpView junitView = new HttpView()
            {
                @Override
                public void renderInternal(Object model, PrintWriter out) throws Exception
                {
                    Map<String, List<Class<? extends TestCase>>> testCases = getTestCases();

                    out.println("<div class=\"normal\"><table class=\"dataRegion\">");

                    for (String module : testCases.keySet())
                    {
                        String moduleTd = module;

                        for (Class<? extends TestCase> clazz : testCases.get(module))
                        {
                            out.println("<tr><td>" + moduleTd + "</td><td>");
                            moduleTd = "&nbsp;";
                            out.println(clazz.getName() + " <a href=\"run.view?testCase=" + clazz.getName() + "\">&lt;run&gt;</a></td></tr>");
                        }

                        out.println("<tr><td colspan=2>&nbsp;</td></tr>");
                    }

                    out.println("</table></div>");

                    out.print("<br><a href=run.view><img border=0 src=\"" + PageFlowUtil.buttonSrc("Run All") + "\" alt=\"Run All\"></a>");
                }
            };

            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            return junitView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin
    public class RunAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            HttpServletRequest request = getViewContext().getRequest();
            TestContext.setTestContext(request, (User) request.getUserPrincipal());
            TestResult result = new TestResult();

            String testCase = request.getParameter("testCase");
            if (null != testCase && 0 == testCase.length())
                testCase = null;

            Map<String, List<Class<? extends TestCase>>> testCases = getTestCases();

            for (String module : testCases.keySet())
            {
                for (Class<? extends TestCase> clazz : testCases.get(module))
                {
                    if (null == testCase || testCase.equals(clazz.getName()))
                        _run(clazz, result);
                }
            }

            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            return new TestResultView(result);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    private Class<? extends TestCase> findTestClass(String testCase)
    {
        Map<String, List<Class<? extends TestCase>>> testCases = getTestCases();

        for (String module : testCases.keySet())
        {
            for (Class<? extends TestCase> clazz : testCases.get(module))
            {
                if (null == testCase || testCase.equals(clazz.getName()))
                    return clazz;
            }
        }

        return null;
    }


    static void _run(Class<? extends TestCase> testCase, TestResult result) throws IllegalAccessException, InstantiationException
    {
        try
        {
            Method m = testCase.getDeclaredMethod("suite", (Class[]) null);
            Test test = (Test) m.invoke(null);
            test.run(result);
        }
        catch (Exception x)
        {
            TestCase dummy = testCase.newInstance();
            result.addError(dummy, x);
        }
    }


    static LinkedList<String> list = new LinkedList<String>();
    static Format format = FastDateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.LONG);

    @RequiresPermission(ACL.PERM_NONE)
    public class AliveAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            synchronized(AliveAction.class)
            {
                HttpServletRequest request = getViewContext().getRequest();
                HttpServletResponse response = getViewContext().getResponse();
                TestContext.setTestContext(request, (User) request.getUserPrincipal());
                TestResult result = new TestResult();

                Class<? extends TestCase> test = findTestClass("org.labkey.api.data.DbSchema$TestCase");

                if (null != test)
                    _run(test, result);

                int status = HttpServletResponse.SC_OK;
                if (result.failureCount() != 0)
                    status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

                String time = format.format(new Date());
                String statusString = "" + status + ": " + time + "    " + request.getHeader("User-Agent");
                if (list.size() > 20)
                    list.removeFirst();
                list.add(statusString);

                response.reset();
                response.setStatus(status);

                PrintWriter out = response.getWriter();
                response.setContentType("text/plain");

                out.println(status == HttpServletResponse.SC_OK ? "OK" : "ERROR");
                out.println();
                out.println("history");
                for (ListIterator it = list.listIterator(list.size()); it.hasPrevious();)
                    out.println(it.previous());

                response.flushBuffer();
                return null;
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class TestResultView extends HttpView
    {
        TestResult _result;


        TestResultView(TestResult result)
        {
            _result = result;
            //setTitle("JUnit test results");
        }


        @Override
        public void renderInternal(Object model, PrintWriter out) throws Exception
        {
            if (_result.wasSuccessful())
                out.print("<br><h2>SUCCESS</h2>");
            else
                out.print("<h2 class=ms-error>FAILURE</h2>");
            out.print("<br><table><tr><td class=ms-searchform>Tests</td><td class=normal align=right>");
            out.print("" + _result.runCount());
            out.print("</td></tr><tr><td class=ms-searchform>Failures</td><td class=normal align=right>");
            out.print("" + _result.failureCount());
            out.print("</td></tr><tr><td class=ms-searchform>Errors</td><td class=normal align=right>");
            out.print("" + _result.errorCount());
            out.print("</td></tr></table>");

            if (_result.errorCount() > 0)
            {
                out.println("<br><table width=\"640\"><td width=100><hr style=\"width:40; height:1;\"></td><td class=\"normal\" nowrap><b>errors</b></td><td width=\"100%\"><hr style=\"height:1;\"></td></tr></table>");
                for (Enumeration e = _result.errors(); e.hasMoreElements();)
                {
                    TestFailure tf = (TestFailure) e.nextElement();
                    out.print(tf);
                    out.print("<br><pre>");
                    tf.thrownException().printStackTrace(out);
                    out.print("</pre>");
                }
            }

            if (_result.failureCount() > 0)
            {
                out.println("<table width=\"640\"><td width=100><hr style=\"width:40; height:1;\"></td><td class=\"normal\" nowrap><b>failures</b></td><td width=\"100%\"><hr style=\"height:1;\"></td></tr></table>");
                for (Enumeration e = _result.failures(); e.hasMoreElements();)
                {
                    TestFailure f = (TestFailure) e.nextElement();
                    if (f.thrownException().getMessage().startsWith("<div>"))
                        out.println(f.thrownException().getMessage() + "<br>");
                    else
                        out.println(PageFlowUtil.filter(f.thrownException().getMessage()) + "<br>");
                    String testName = f.failedTest().getClass().getName();
                    int count=0;
                    for (StackTraceElement ste : f.thrownException().getStackTrace())
                    {
                         if (ste.getClassName().equals(testName))
                        {
                            out.print(PageFlowUtil.filter(ste.toString()));
                            out.println("<br>");
                            count++;
                            if (count >= 3)
                                break;
                        }
                    }
                    out.println("<p/>");
                }
            }
        }
    }
}
