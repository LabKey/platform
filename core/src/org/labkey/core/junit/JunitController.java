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

package org.labkey.core.junit;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONArray;
import org.json.old.JSONObject;
import org.junit.runner.notification.Failure;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.PermissionCheckableAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.StatusAppender;
import org.labkey.api.action.StatusReportingRunnable;
import org.labkey.api.action.StatusReportingRunnableAction;
import org.labkey.api.jsp.JspTest;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


public class JunitController extends SpringActionController
{
    private static final ActionResolver _resolver = new DefaultActionResolver(JunitController.class);


    public JunitController()
    {
        setActionResolver(_resolver);
    }

    public static class JUnitViewBean
    {
        public final Map<String, List<Class>> testCases;
        public final boolean showRunButtons;

        JUnitViewBean(Map<String, List<Class>> tests, boolean buttons)
        {
            this.testCases = tests;
            this.showRunButtons = buttons;
        }
    }


    @RequiresSiteAdmin
    public class BeginAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            return new JspView("/org/labkey/core/junit/runner.jsp",
                    new JUnitViewBean(JunitManager.getTestCases(), true),
                    errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Unit and integration tests");
        }
    }


    static public TestWhen.When getScope(Class cls)
    {
        TestWhen ann = (TestWhen)cls.getAnnotation(TestWhen.class);
        if (null != ann)
            return ann.value();
        if (JspTest.class.isAssignableFrom(cls))
        {
            if (JspTest.DRT.class.isAssignableFrom(cls))
                return TestWhen.When.DRT;
            if (JspTest.BVT.class.isAssignableFrom(cls))
                return TestWhen.When.BVT;
            if (JspTest.DAILY.class.isAssignableFrom(cls))
                return TestWhen.When.DAILY;
            if (JspTest.WEEKLY.class.isAssignableFrom(cls))
                return TestWhen.When.WEEKLY;
            if (JspTest.PERFORMANCE.class.isAssignableFrom(cls))
                return TestWhen.When.PERFORMANCE;
        }
        return TestWhen.When.DRT;
    }


    @RequiresSiteAdmin
    public class RunAction extends SimpleViewAction<TestForm>
    {
        @Override
        public ModelAndView getView(TestForm form, BindException errors) throws Exception
        {
            List<Class> testClasses = getTestClasses(form);
            TestContext.setTestContext(getViewContext().getRequest(), getUser());
            List<JunitRunner.RunnerResult> results = new LinkedList<>();

            for (Class testClass : testClasses)
            {
                // check if the client has gone away
                getViewContext().getResponse().getWriter().print(" ");
                getViewContext().getResponse().flushBuffer();
                if (form.getMethodName() == null)
                    results.add(JunitRunner.run(testClass));
                else
                    results.add(JunitRunner.run(testClass, form.getMethodName()));
            }

            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            return new TestResultView(testClasses, results);
        }


        private List<Class> getTestClasses(TestForm form)
        {
            List<Class> testClasses = new LinkedList<>();

            if (!StringUtils.isEmpty(form.getModule()))
            {
                // This is branch taken when you select a group of unit tests, like Core or Study.
                // Performance tests will not be run in this case.
                testClasses.addAll(JunitManager.getTestCases().get(form.getModule()));
            }
            else if (!StringUtils.isEmpty(form.getTestCase()))
            {
                // This is the branch taken when you select a specific unit test from the UI.
                // To allow performance tests to be selected change the scope to PERFORMANCE.
                form._scope = TestWhen.When.PERFORMANCE;

                for (List<Class> list : JunitManager.getTestCases().values())
                {
                    list.stream()
                        .filter((test) -> test.getName().equals(form.getTestCase()))
                        .forEach(testClasses::add);
                }
            }
            else
            {
                // You end up here if you select "All", "BVT" or "DRT"  unit tests.
                JunitManager.getTestCases().values().forEach(testClasses::addAll);
            }

            // filter by scope
            List<Class> ret;
            ret = testClasses.stream()
                    .filter((test)->getScope(test).ordinal()<=form._scope.ordinal())
                    .collect(Collectors.toList());
            return ret;
        }


        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Tests", new ActionURL(BeginAction.class,getContainer()));
            root.addChild("Results");
        }
    }


    private static final String RESULTS_SESSION_KEY = "JUnit_Results";

    @RequiresSiteAdmin
    public class Run3Action extends SimpleViewAction<TestForm>
    {
        @Override
        public ModelAndView getView(TestForm form, BindException errors) throws Exception
        {
            HttpSession session = getViewContext().getRequest().getSession(true);
            @SuppressWarnings({"unchecked"})
            List<JunitRunner.RunnerResult> results = (List<JunitRunner.RunnerResult>)session.getAttribute(RESULTS_SESSION_KEY);
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
            String module = form.getModule();

            if (null != module)
            {
                List<Class> moduleTests = JunitManager.getTestCases().get(module);
                if (moduleTests == null || moduleTests.isEmpty())
                {
                    throw new NotFoundException("No tests for module: " + module);
                }
                return moduleTests;
            }

            Set<Class> allTestClasses = new LinkedHashSet<>();
            JunitManager.getTestCases()
                    .values()
                    .forEach(moduleTests -> allTestClasses.addAll(moduleTests));

            final String testCase = form.getTestCase();
            if (!StringUtils.isBlank(testCase))
            {
                Class specifiedTest = allTestClasses.parallelStream()
                        .filter(clazz -> testCase.equals(clazz.getName()))
                        .findAny()
                        .orElseThrow(() -> new NotFoundException("No such test: " + testCase));
                return Collections.singletonList(specifiedTest);
            }

            return List.copyOf(allTestClasses);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
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
            List<JunitRunner.RunnerResult> results = new LinkedList<>();
            return new JunitRunnable(testClasses, results, getViewContext().getRequest(), getUser());
        }
    }


    private static class JunitRunnable implements StatusReportingRunnable
    {
        private final StatusAppender _appender;
        private final Logger _log;
        private final List<Class> _testClasses;
        private final List<JunitRunner.RunnerResult> _results;
        private volatile boolean _running = true;

        private JunitRunnable(List<Class> testClasses, List<JunitRunner.RunnerResult> results, HttpServletRequest request, User user) // TODO: Make this a Callable instead?
        {
            _testClasses = testClasses;
            _results = results;
            _appender = new StatusAppender("StatusAppender", null, PatternLayout.createDefaultLayout(), false, null);
            _log = LogManager.getLogger(JunitRunnable.class);
            LoggerContext loggerContext = (LoggerContext) LogManager.getContext(true);
            Configuration configuration = loggerContext.getConfiguration();
            LoggerConfig loggerConfig = configuration.getLoggerConfig(_log.getName());
            loggerConfig.addAppender(_appender, Level.toLevel(_log.getLevel().toString()), null);
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
    public static class Testlist extends ReadOnlyApiAction
    {
        @Override
        public ApiResponse execute(Object o, BindException errors)
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
                    TestWhen.When scope = getScope(clazz);

                    // Send back both the class name and the timeout
                    Map<String, Object> testClass = new HashMap<>();
                    testClass.put("module", module);
                    testClass.put("className", clazz.getName());
                    testClass.put("timeout", timeout);
                    testClass.put("when", scope.name());
                    tests.add(testClass);
                }
            }

            return new ApiSimpleResponse(values);
        }
    }

    @RequiresSiteAdmin
    public static class GoAction extends MutatingApiAction<TestForm>
    {
        @Override
        public Object execute(TestForm form, BindException errors) throws Exception
        {
            TestContext.setTestContext(getViewContext().getRequest(), getUser());

            String testCase = form.getTestCase();
            if (testCase == null)
                throw new RuntimeException("testCase parameter required");

            Class clazz = findTestClass(form.getTestCase());
            JunitRunner.RunnerResult result = JunitRunner.run(clazz);

            if (!result.junitResult.wasSuccessful())
                getViewContext().getResponse().setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            Map<String, Object> map = new HashMap<>();

            map.put("runCount", result.junitResult.getRunCount());
            map.put("failureCount", result.junitResult.getFailureCount());
            map.put("wasSuccessful", result.junitResult.wasSuccessful());
            map.put("failures", toList(result.junitResult.getFailures()));
            if (result.junitResult.getIgnoreCount() > 0)
                map.put("ignored", result.junitResult.getIgnoreCount());
            JSONArray timers = new JSONArray();
            if (null != result.perfResults && !result.perfResults.isEmpty())
            {
                result.perfResults.forEach(cputimer -> {
                    var t = new JSONObject();
                    t.put("name", cputimer.getName());
                    t.put("ms", cputimer.getTotalMilliseconds());
                    timers.put(t);
                });
            }
            map.put("timers", timers);

            return new ApiSimpleResponse(map);
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
    }

    public static class TestForm
    {
        private String _module;
        private String _testCase;
        private String _methodName;
        private TestWhen.When _scope = TestWhen.When.WEEKLY;

        public String getTestCase()
        {
            return _testCase;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setTestCase(String testCase)
        {
            _testCase = testCase;
        }

        public String getMethodName()
        {
            return _methodName;
        }

        public void setMethodName(String methodName)
        {
            _methodName = methodName;
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
                _scope = w;
            }
            catch (IllegalArgumentException e)
            {
                /* */
            }
        }
    }


    private static Class findTestClass(String testCase)
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
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            synchronized(AliveAction.class)
            {
                HttpServletRequest request = getViewContext().getRequest();
                HttpServletResponse response = getViewContext().getResponse();
                TestContext.setTestContext(request, (User) request.getUserPrincipal());

                Class clazz = findTestClass("org.labkey.api.data.DbSchema$TestCase");
                JunitRunner.RunnerResult result = new JunitRunner.RunnerResult();

                if (null != clazz)
                    result = JunitRunner.run(clazz);

                int status = HttpServletResponse.SC_OK;
                if (result.junitResult.getFailureCount() != 0 || 0 == result.junitResult.getRunCount())
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

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    private static class TestResultView extends HttpView
    {
        private final List<Class> _tests;
        private final List<JunitRunner.RunnerResult> _results;
        private final List<Failure> _failures = new LinkedList<>();
        private int _runCount = 0;
        private int _failureCount = 0;

        TestResultView(List<Class> tests, List<JunitRunner.RunnerResult> results)
        {
            _tests = tests;
            _results = results;
            for (JunitRunner.RunnerResult result : results)
            {
                _runCount += result.junitResult.getRunCount();
                _failureCount += result.junitResult.getFailureCount();
                _failures.addAll(result.junitResult.getFailures());
            }

            assert _failureCount == _failures.size();
        }


        @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
        @Override
        public void renderInternal(Object model, PrintWriter out)
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
            out.println("<table class=\"table\">");
            for (int i=0 ; i<_results.size() && i<_tests.size() ; i++)
            {
                final String testName = _tests.get(i).getName();
                out.print("<tr><th align=left valign=top>");
                out.println(PageFlowUtil.filter(testName));
                out.println("</th><td align=right>");
                long time = _results.get(i).junitResult.getRunTime();
                if (time < 10_000)
                    out.println(time/1000.0 + "s");
                else
                    out.println(time/1000 + "s");
                var timers = _results.get(i).perfResults;
                if (!timers.isEmpty())
                {
                    out.println("<table class=\"table-condensed\">");
                    List<String> strOut = new ArrayList<>(Arrays.asList(CPUTimer.header().split("\t")));
                    out.println("<tr>");
                    strOut.forEach((str)->{
                        if(str.trim().length()==0)
                            out.println("<td align=left>Name</td>");
                        else
                            out.println("<td align=right>" + PageFlowUtil.filter(str.trim()) + "</td>");
                    });
                    out.println("</tr>");

                    _results.get(i).perfResults.forEach(timer ->
                    {
                        String[] strTmp = timer.toString().split("\t");
                        out.println("<tr><td class=\"TIMER_NAME\">" + PageFlowUtil.filter(strTmp[0].trim()) + "</td>");
                        out.println("<td align=right class=\"TIMER_VALUE\" data-test=\"" + PageFlowUtil.filter(testName) + "\" timer-name=\"" + PageFlowUtil.filter(strTmp[0].trim()) + "\" data-name=\"" + PageFlowUtil.filter(strTmp[0].trim()) + "_cumulative\" data-ms=\"" + strTmp[1].trim() + "\">" + strTmp[1].trim() + "ms</td>");
                        out.println("<td align=right class=\"TIMER_VALUE\" data-test=\"" + PageFlowUtil.filter(testName) + "\" timer-name=\"" + PageFlowUtil.filter(strTmp[0].trim()) + "\" data-name=\"" + PageFlowUtil.filter(strTmp[0].trim()) + "_min\" data-ms=\"" + strTmp[2].trim() + "\">" + strTmp[2].trim() + "ms</td>");
                        out.println("<td align=right class=\"TIMER_VALUE\" data-test=\"" + PageFlowUtil.filter(testName) + "\" timer-name=\"" + PageFlowUtil.filter(strTmp[0].trim()) + "\" data-name=\"" + PageFlowUtil.filter(strTmp[0].trim()) + "_max\" data-ms=\"" + strTmp[3].trim() + "\">" + strTmp[3].trim() + "ms</td>");
                        out.println("<td align=right class=\"TIMER_VALUE\" data-test=\"" + PageFlowUtil.filter(testName) + "\" timer-name=\"" + PageFlowUtil.filter(strTmp[0].trim()) + "\" data-name=\"" + PageFlowUtil.filter(strTmp[0].trim()) + "_avg\" data-ms=\"" + strTmp[4].trim() + "\">" + strTmp[4].trim() + "ms</td>");
                        out.println("<td align=right class=\"TIMER_VALUE\" data-test=\"" + PageFlowUtil.filter(testName) + "\" timer-name=\"" + PageFlowUtil.filter(strTmp[0].trim()) + "\" data-name=\"" + PageFlowUtil.filter(strTmp[0].trim()) + "_calls\" data-ms=\"" + strTmp[5].trim() + "\">" + strTmp[5].trim() + "</td>");
                        out.println("</tr>");
                    });
                    out.println("</table>");
                }
                out.println("</td></tr>");
            }
            out.println("</table>");
        }
    }


    @RequiresNoPermission
    public class EchoFormAction extends PermissionCheckableAction
    {
        @Override
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


    private static List<String> filterStackTrace(Throwable t)
    {
        List<String> lines = new ArrayList<>();
        lines.add(t.toString());

        for (StackTraceElement ste : t.getStackTrace())
        {
            String line = ste.toString();

            if (line.startsWith("org.junit.internal.") || line.startsWith("sun.reflect.") || line.startsWith("java.lang.reflect."))
                break;

            lines.add("\tat " + ste.toString());
        }

        return lines;
    }

    public static String renderTrace(Throwable t)
    {
        return StringUtils.join(filterStackTrace(t), "\n");
    }

    private static void outputStackTrace(Throwable t, PrintWriter out)
    {
        List<String> lines = filterStackTrace(t);
        for (String line : lines)
        {
            out.println(PageFlowUtil.filter(line, true));
        }
    }

    private static String h(String s)
    {
        return PageFlowUtil.filter(s);
    }
}
