/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.PermissionCheckableAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.StatusAppender;
import org.labkey.api.action.StatusReportingRunnable;
import org.labkey.api.action.StatusReportingRunnableAction;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.Format;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.stream.Collectors;


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
                        ActionURL moduleURL = new ActionURL(RunAction.class, getContainer()).addParameter("module", module);

                        out.println("<tr><td colspan=3>");
                        out.println("<a href=\"" + PageFlowUtil.filter(moduleURL.getLocalURIString()) + "\">" + module + "</a>");
                        out.println("</td></tr>");

                        for (Class clazz : testCases.get(module))
                        {
                            ActionURL testCaseURL = new ActionURL(RunAction.class, getContainer()).addParameter("testCase", clazz.getName());
                            out.println("<tr>");
                            out.println("<td style=\"min-width:60px;\">&nbsp;</td>");
                            out.println("<td style=\"font-size:66%; color:gray;\">" + getWhen(clazz) + "&nbsp;&nbsp;</td>");
                            out.println("<td> <a href=\"" + PageFlowUtil.filter(testCaseURL.getLocalURIString()) + "\">" + clazz.getName() + "</a></td>");
                            out.println("</tr>");
                        }
                        out.println("<tr><td colspan=3>&nbsp;</td></tr>");
                    }

                    out.println("</table></div>");

                    out.print("<p><br>" + PageFlowUtil.button("Run All").href(new ActionURL(RunAction.class, getContainer())) + "</p>");
                    out.print("<p><br>" + PageFlowUtil.button("Run BVT").href(new ActionURL(RunAction.class, getContainer()).addParameter("when","BVT")) + "</p>");
                    out.print("<p><br>" + PageFlowUtil.button("Run DRT").href(new ActionURL(RunAction.class, getContainer()).addParameter("when","DRT")) + "</p>");

                    out.print("<form name=\"run2\" action=\"" +  new ActionURL(Run2Action.class, getContainer()) + "\" method=\"post\">" + PageFlowUtil.button("Run In Background #1 (Experimental)").submit(true) + "</form>");
                    out.print("<br>" + PageFlowUtil.button("Run In Background #2 (Experimental)").href(new ActionURL(Run3Action.class, getContainer())));
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


    static private TestWhen.When getWhen(Class cls)
    {
        TestWhen ann = (TestWhen)cls.getAnnotation(TestWhen.class);
        if (null == ann)
            return TestWhen.When.DRT;
        return ann.value();
    }


    @RequiresSiteAdmin
    public class RunAction extends SimpleViewAction<TestForm>
    {
        public ModelAndView getView(TestForm form, BindException errors) throws Exception
        {
            List<Class> testClasses = getTestClasses(form);
            TestContext.setTestContext(getViewContext().getRequest(), getUser());
            List<Result> results = new LinkedList<>();

            for (Class testClass : testClasses)
            {
                // check if the client has gone away
                getViewContext().getResponse().getWriter().print(" ");
                getViewContext().getResponse().flushBuffer();
                results.add(JunitRunner.run(testClass));
            }

            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            return new TestResultView(testClasses, results);
        }


        private List<Class> getTestClasses(TestForm form)
        {
            List<Class> testClasses = new LinkedList<>();

            if (!StringUtils.isEmpty(form.getModule()))
            {
                testClasses.addAll(JunitManager.getTestCases().get(form.getModule()));
            }
            else if (!StringUtils.isEmpty(form.getTestCase()))
            {
                for (List<Class> list : JunitManager.getTestCases().values())
                {
                    list.stream()
                        .filter((test) -> test.getName().equals(form.getTestCase()))
                        .forEach(testClasses::add);
                }
            }
            else
            {
                JunitManager.getTestCases().values().forEach(testClasses::addAll);
            }

            // filter by TestWhen
            List<Class> ret;
            ret = testClasses.stream()
                    .filter((test)->getWhen(test).ordinal()<=form._when.ordinal())
                    .collect(Collectors.toList());
            return ret;
        }


        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Tests", new ActionURL(BeginAction.class,getContainer()));
            root.addChild("Results");
            return root;
        }
    }


    private static final String RESULTS_SESSION_KEY = "JUnit_Results";

    @RequiresSiteAdmin
    public class Run3Action extends SimpleViewAction<TestForm>
    {
        public ModelAndView getView(TestForm form, BindException errors) throws Exception
        {
            HttpSession session = getViewContext().getRequest().getSession(true);
            @SuppressWarnings({"unchecked"})
            List<Result> results = (List<Result>)session.getAttribute(RESULTS_SESSION_KEY);
            ModelAndView view;

            if (null != results)
            {
                session.removeAttribute(RESULTS_SESSION_KEY);
                view = new TestResultView(new ArrayList<Class>(), results);
            }
            else
            {
                List<Class> testClasses = getTestClasses(form);
                TestContext.setTestContext(getViewContext().getRequest(), getUser());
                getPageConfig().setTemplate(PageConfig.Template.Dialog);
                results = new LinkedList<>();
                HttpServletResponse response = getViewContext().getResponse();
                response.setContentType("text/plain");

                for (Class testClass : testClasses)
                {
                    // show status.  this also stops the tests if the client goes away.
                    response.getWriter().println(testClass.getName());
                    response.flushBuffer();
                    results.add(JunitRunner.run(testClass));
                }

                // TODO: Probably won't work... looks like junit Result is not Serializable
                session.setAttribute(RESULTS_SESSION_KEY, results);
                view = null;  // TODO: Plus we can't redirect with plain text...
            }

            return view;
        }

        private List<Class> getTestClasses(TestForm form)
        {
            Map<String, List<Class>> allTestClasses = JunitManager.getTestCases();

            String module = form.getModule();

            if (null != module)
                return JunitManager.getTestCases().get(module);

            List<Class> testClasses = new LinkedList<>();
            String testCase = form.getTestCase();

            if (null == testCase || 0 != testCase.length())
            {
                for (String m : allTestClasses.keySet())
                {
                    for (Class clazz : allTestClasses.get(m))
                    {
                        // include test
                        if (null == testCase || testCase.equals(clazz.getName()))
                            testClasses.add(clazz);
                    }
                }
            }

            return testClasses;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin
    public class Run2Action extends StatusReportingRunnableAction
    {
        private List<Class> getTestClasses(TestForm form)
        {
            Map<String, List<Class>> allTestClasses = JunitManager.getTestCases();

            String module = form.getModule();

            if (null != module)
                return JunitManager.getTestCases().get(module);

            List<Class> testClasses = new LinkedList<>();
            String testCase = form.getTestCase();

            if (null == testCase || 0 != testCase.length())
            {
                for (String m : allTestClasses.keySet())
                {
                    for (Class clazz : allTestClasses.get(m))
                    {
                        // include test
                        if (null == testCase || testCase.equals(clazz.getName()))
                            testClasses.add(clazz);
                    }
                }
            }

            return testClasses;
        }

        @Override
        protected StatusReportingRunnable newStatusReportingRunnable()
        {
            List<Class> testClasses = getTestClasses(new TestForm());
            List<Result> results = new LinkedList<>();
            return new JunitRunnable(testClasses, results, getViewContext().getRequest(), getUser());
        }
    }


    private static class JunitRunnable implements StatusReportingRunnable
    {
        private final StatusAppender _appender;
        private final Logger _log;
        private final List<Class> _testClasses;
        private final List<Result> _results;
        private volatile boolean _running = true;

        private JunitRunnable(List<Class> testClasses, List<Result> results, HttpServletRequest request, User user) // TODO: Make this a Callable instead?
        {
            _testClasses = testClasses;
            _results = results;
            _appender = new StatusAppender();
            _log = Logger.getLogger(JunitRunnable.class);
            _log.addAppender(_appender);
            TestContext.setTestContext(request, user);
        }

        @Override
        public boolean isRunning()
        {
            return _running;
        }

        @Override
        public Collection<String> getStatus(@Nullable Integer offset)
        {
            return _appender.getStatus(offset);
        }

        @Override
        public void run()
        {
            for (Class testClass : _testClasses)
            {
                _log.info("Running " + testClass.getName());
                _results.add(JunitRunner.run(testClass));
            }

            _running = false;
        }
    }


    // Used by DRT JUnitTest to retrieve the current list of tests
    @SuppressWarnings({"UnusedDeclaration"})
    @RequiresSiteAdmin
    public static class Testlist extends ApiAction
    {
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            Map<String, List<Class>> testCases = JunitManager.getTestCases();

            Map<String, List<Map<String, Object>>> values = new HashMap<>();
            for (String module : testCases.keySet())
            {
                List<Map<String, Object>> tests = new ArrayList<>();
                values.put("Remote " + module, tests);
                for (Class<Object> clazz : testCases.get(module))
                {
                    int timeout = TestTimeout.DEFAULT;
                    // Check if the test has requested a non-standard timeout
                    TestTimeout testTimeout = clazz.getAnnotation(TestTimeout.class);
                    if (testTimeout != null)
                    {
                        timeout = testTimeout.value();
                    }
                    if (testTimeout != null)
                    {
                        timeout = testTimeout.value();
                    }
                    TestWhen.When when = getWhen(clazz);

                    // Send back both the class name and the timeout
                    Map<String, Object> testClass = new HashMap<>();
                    testClass.put("module", module);
                    testClass.put("className", clazz.getName());
                    testClass.put("timeout", timeout);
                    testClass.put("when", when.name());
                    tests.add(testClass);
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
            TestContext.setTestContext(getViewContext().getRequest(), getUser());

            String testCase = form.getTestCase();
            if (testCase == null)
                throw new RuntimeException("testCase parameter required");

            Class clazz = Class.forName(testCase);
            Result result = JunitRunner.run(clazz);

            int status = HttpServletResponse.SC_OK;
            if (!result.wasSuccessful())
                status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

            Map<String, Object> map = new HashMap<>();

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
            List<Map<String, Object>> list = new ArrayList<>(failures.size());

            for (Failure failure : failures)
            {
                Map<String, Object> map = new HashMap<>();
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
        private String _module;
        private String _testCase;
        private TestWhen.When _when = TestWhen.When.WEEKLY;

        public String getTestCase()
        {
            return _testCase;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setTestCase(String testCase)
        {
            _testCase = testCase;
        }

        public String getModule()
        {
            return _module;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setModule(String module)
        {
            _module = module;
        }

        public void setWhen(String when)
        {
            try
            {
                TestWhen.When w = TestWhen.When.valueOf(when);
                _when = w;
            }
            catch (IllegalArgumentException e)
            {
                /* */
            }
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


    private static final LinkedList<String> list = new LinkedList<>();
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
        private final List<Class> _tests;
        private final List<Result> _results;
        private final List<Failure> _failures = new LinkedList<>();
        private int _runCount = 0;
        private int _failureCount = 0;

        TestResultView(List<Class> tests, List<Result> results)
        {
            this._tests = tests;
            this._results = results;
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

            out.print("<br><table><tr><td style=\"padding-right: 10px; font-weight: bold;\">Tests:</td><td align=right>");
            out.print(_runCount);
            out.print("</td></tr><tr><td style=\"padding-right: 10px; font-weight: bold;\">Failures:</td><td align=right>");
            out.print(_failureCount);
            out.print("</td></tr></table>");

            if (_failureCount > 0)
            {
                out.println("<table width=\"640\"><tr><td width=100><hr style=\"width:40; height:1;\"></td><td nowrap><b>failures</b></td><td width=\"100%\"><hr style=\"height:1;\"></td></tr></table>");

                for (Failure failure : _failures)
                {
                    out.println("<b>" + h(failure.getDescription().toString()) + "</b><br>");
                    Throwable t = failure.getException();
                    String message = t.getMessage();

                    if (message != null && message.startsWith("<div>"))
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

            out.println("<p></p><p></p>");
            out.println("<table>");
            for (int i=0 ; i<_results.size() && i<_tests.size() ; i++)
            {
                out.print("<tr><td align=left>");
                out.println(PageFlowUtil.filter(_tests.get(i).getName()));
                out.println("</td><td align=right>");
                long time = _results.get(i).getRunTime();
                if (time < 10_000)
                    out.println(time/1000.0);
                else
                    out.println(time/1000);
                out.println("</td></tr>");
            }
            out.println("</table>");
        }
    }


    @RequiresNoPermission
    public class EchoFormAction extends PermissionCheckableAction
    {
        public ModelAndView handleRequest(HttpServletRequest req, HttpServletResponse res) throws Exception
        {
            PrintWriter out = res.getWriter();
            out.println("<html><head></head><body><form method=GET>");
            IteratorUtils.asIterator(req.getParameterNames()).forEachRemaining(name -> {
                out.print("<input name='");
                out.print(h(name));
                out.print("' value='");
                out.print(h(req.getParameter(name)));
                out.print("'>");
                out.print(h(name));
                out.println("<br>");
            });

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
