/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import junit.framework.TestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.TestContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/*
* User: adam
* Date: Jul 5, 2009
* Time: 10:54:59 AM
*/
public class JunitRunner
{
    private static final Logger LOG = LogManager.getLogger(JunitRunner.class);

    public static class RunnerResult
    {
        Result junitResult = new Result();
        ArrayList<CPUTimer> perfResults;
        Map<Description, List<CPUTimer>> individualPerfResults;
    }

    static RunnerResult run(Class clazz, String method)
    {
        Request request = Request.method(clazz, method);
        return run(request);
    }

    static RunnerResult run(Class clazz)
    {
        assert !TestCase.class.isAssignableFrom(clazz);
        Request request = Request.classes(clazz);
        return run(request);
    }

    private static RunnerResult run(Request request)
    {
        Runner runner = request.getRunner();
        Description desc = runner.getDescription();
        ArrayList<Description> children = desc.getChildren();
        String description = children.stream()
            .map(Description::toString)
            .collect(Collectors.joining(", "));

        try
        {
            if (desc.testCount() > 1)
                LOG.info("Starting suite: " + description + " (" + desc.testCount() + " tests)");

            ArrayList<CPUTimer> allTimers = new ArrayList<>();
            Map<Description, ArrayList<CPUTimer>> testTimers = new LinkedHashMap<>();

            JUnitCore core = new JUnitCore();
            core.addListener(new RunListener() {
                @Override
                public void testStarted(Description description)
                {
                    LOG.debug("Starting test: " + description);
                    TestContext.get().clearPerfResults();
                }

                @Override
                public void testFinished(Description description)
                {
                    LOG.debug("Finished test: " + description);
                    ArrayList<CPUTimer> timers = TestContext.get().getPerfResults();
                    testTimers.put(description, timers);
                    allTimers.addAll(timers);
                }

                @Override
                public void testFailure(Failure failure)
                {
                    Throwable t = failure.getException();
                    LOG.error("Test failed: " + failure.getDescription() + ":\n" + JunitController.renderTrace(t));
                }
            });
            Result result = core.run(request);

            RunnerResult r = new RunnerResult();
            r.junitResult = result;
            r.perfResults = allTimers;
            if (r.junitResult.wasSuccessful())
                r.perfResults = allTimers;
            return r;
        }
        finally
        {
            TestContext.get().clearPerfResults();
            if (desc.testCount() > 1)
                LOG.info("Completed suite: " + description);
        }
    }
}
