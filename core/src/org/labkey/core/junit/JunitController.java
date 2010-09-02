/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang.time.FastDateFormat;
import org.json.JSONObject;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.Format;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;


public class JunitController extends SpringActionController
{
    private static final ActionResolver _resolver = new DefaultActionResolver(JunitController.class);


    public JunitController()
    {
        setActionResolver(_resolver);
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
                    Map<String, List<Class>> testCases = JunitManager.getTestCases();

                    out.println("<div><table class=\"labkey-data-region\">");

                    for (String module : testCases.keySet())
                    {
                        String moduleTd = module;

                        for (Class clazz : testCases.get(module))
                        {
                            out.println("<tr><td>" + moduleTd + "</td><td>");
                            out.println(clazz.getName() + " <a href=\"run.view?testCase=" + clazz.getName() + "\">&lt;run&gt;</a></td></tr>");
                            moduleTd = "&nbsp;";
                        }

                        out.println("<tr><td colspan=2>&nbsp;</td></tr>");
                    }

                    out.println("</table></div>");

                    out.print("<br>" + PageFlowUtil.generateButton("Run All", new ActionURL(RunAction.class, getContainer())));
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
    public class RunAction extends SimpleViewAction<TestForm>
    {
        public ModelAndView getView(TestForm form, BindException errors) throws Exception
        {
            TestContext.setTestContext(getViewContext().getRequest(), getUser());

            String testCase = form.getTestCase();
            if (null != testCase && 0 == testCase.length())
                testCase = null;

            Map<String, List<Class>> testCases = JunitManager.getTestCases();
            List<Result> results = new LinkedList<Result>();

            for (String module : testCases.keySet())
            {
                for (Class clazz : testCases.get(module))
                {
                    // check if the client has gone away
                    getViewContext().getResponse().getWriter().print(" ");
                    getViewContext().getResponse().flushBuffer();

                    // run test
                    if (null == testCase || testCase.equals(clazz.getName()))
                        results.add(JunitRunner.run(clazz));
                }
            }

            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            return new TestResultView(results);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    // Used by JUnitTest to retrieve the current list of tests
    @SuppressWarnings({"UnusedDeclaration"})
    @RequiresSiteAdmin
    public static class Testlist extends ApiAction
    {
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            Map<String, List<Class>> testCases = JunitManager.getTestCases();

            Map<String, List<String>> values = new HashMap<String, List<String>>();
            for (String module : testCases.keySet())
            {
                List<String> tests = new ArrayList<String>();
                values.put("Remote " + module, tests);
                for (Class clazz : testCases.get(module))
                {
                    tests.add(clazz.getName());
                }
            }

            return new ApiSimpleResponse(values);
        }
    }

    @RequiresSiteAdmin
    public static class Go extends SimpleViewAction<TestForm>
    {
        public ModelAndView getView(TestForm form, BindException errors) throws Exception
        {
            TestContext.setTestContext(getViewContext().getRequest(), getViewContext().getUser());

            String testCase = form.getTestCase();
            if (testCase == null)
                throw new RuntimeException("testCase parameter required");

            Class clazz = Class.forName(testCase);
            Result result = JunitRunner.run(clazz);

            int status = HttpServletResponse.SC_OK;
            if (!result.wasSuccessful())
                status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

            Map<String, Object> map = new HashMap<String, Object>();

            map.put("runCount", result.getRunCount());
            map.put("failureCount", result.getFailureCount());
            map.put("wasSuccessful", result.wasSuccessful());
            map.put("failures", toList(result.getFailures()));

            JSONObject json = new JSONObject(map);

            HttpServletResponse response = getViewContext().getResponse();
            response.reset();
            response.setStatus(status);

            PrintWriter out = response.getWriter();
            response.setContentType("text/plain");
            response.setCharacterEncoding("utf-8");

            out.append(json.toString(4));
            response.flushBuffer();

            return null;
        }

        private static List<Map<String, Object>> toList(List<Failure> failures)
        {
            List<Map<String, Object>> list = new ArrayList<Map<String, Object>>(failures.size());

            for (Failure failure : failures)
            {
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("failedTest", failure.getTestHeader());
                map.put("isFailure", true);
                //noinspection ThrowableResultOfMethodCallIgnored
                map.put("exceptionMessage", failure.getException().getMessage());
                map.put("trace", failure.getTrace());
                list.add(map);
            }

            return list;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class TestForm
    {
        private String _testCase;

        public String getTestCase()
        {
            return _testCase;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setTestCase(String testCase)
        {
            _testCase = testCase;
        }
    }


    private Class findTestClass(String testCase)
    {
        Map<String, List<Class>> testCases = JunitManager.getTestCases();

        for (String module : testCases.keySet())
        {
            for (Class clazz : testCases.get(module))
            {
                if (null == testCase || testCase.equals(clazz.getName()))
                    return clazz;
            }
        }

        return null;
    }


    private static final LinkedList<String> list = new LinkedList<String>();
    private static final Format format = FastDateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.LONG);

    @SuppressWarnings({"UnusedDeclaration"})
    @RequiresNoPermission
    public class AliveAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            synchronized(AliveAction.class)
            {
                HttpServletRequest request = getViewContext().getRequest();
                HttpServletResponse response = getViewContext().getResponse();
                TestContext.setTestContext(request, (User) request.getUserPrincipal());

                Class clazz = findTestClass("org.labkey.api.data.DbSchema$TestCase");
                Result result = new Result();

                if (null != clazz)
                    result = JunitRunner.run(clazz);

                int status = HttpServletResponse.SC_OK;
                if (result.getFailureCount() != 0 || 0 == result.getRunCount())
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


    private static class TestResultView extends HttpView
    {
        private final List<Failure> _failures = new LinkedList<Failure>();
        private int _runCount = 0;
        private int _failureCount = 0;

        TestResultView(List<Result> results)
        {
            for (Result result : results)
            {
                _runCount += result.getRunCount();
                _failureCount += result.getFailureCount();
                _failures.addAll(result.getFailures());
            }

            assert _failureCount == _failures.size();
        }


        @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
        @Override
        public void renderInternal(Object model, PrintWriter out) throws Exception
        {
            if (0 == _failureCount)
                out.print("<br><h2>SUCCESS</h2>");
            else
                out.print("<h2 class=ms-error>FAILURE</h2>");

            out.print("<br><table><tr><td class=labkey-form-label>Tests</td><td align=right>");
            out.print("" + _runCount);
            out.print("</td></tr><tr><td class=labkey-form-label>Failures</td><td align=right>");
            out.print("" + _failureCount);
            out.print("</td></tr></table>");

            if (_failureCount > 0)
            {
                out.println("<table width=\"640\"><tr><td width=100><hr style=\"width:40; height:1;\"></td><td nowrap><b>failures</b></td><td width=\"100%\"><hr style=\"height:1;\"></td></tr></table>");

                for (Failure failure : _failures)
                {
                    out.println("<b>" + failure.getDescription() + "</b><br>");
                    Throwable t = failure.getException();
                    String message = t.getMessage();

                    if (message.startsWith("<div>"))
                    {
                        out.println("<br>" + message + "<br>");
                    }
                    else
                    {
                        out.print("<pre>");

                        outputStackTrace(t, out);

                        Throwable cause = t.getCause();
                        if (cause != null)
                            outputStackTrace(cause, out);

                        out.println("</pre>");
                    }
                }
            }
        }
    }


    @RequiresNoPermission
    public class EchoFormAction implements Controller
    {
        public ModelAndView handleRequest(HttpServletRequest req, HttpServletResponse res) throws Exception
        {
            PrintWriter out = res.getWriter();
            out.println("<html><head></head><body><form method=GET>");
            Enumeration<String> names = req.getParameterNames();
            while (names.hasMoreElements())
            {
                String name = names.nextElement();
                out.print("<input name='");
                out.print(h(name));
                out.print("' value='");
                out.print(h(req.getParameter(name)));
                out.print("'>");
                out.print(h(name));
                out.println("<br>");
            }

            out.println("<input type=submit></body></html>");
            return null;
        }
    }


    private static void outputStackTrace(Throwable t, PrintWriter out)
    {
        out.println(h(t.toString()));

        for (StackTraceElement ste : t.getStackTrace())
        {
            String line = ste.toString();

            if (line.startsWith("org.junit.internal.") || line.startsWith("sun.reflect.") || line.startsWith("java.lang.reflect."))
                break;

            out.println(PageFlowUtil.filter("\tat " + ste.toString(), true));
        }
    }

    private static String h(String s)
    {
        return PageFlowUtil.filter(s);
    }
}
