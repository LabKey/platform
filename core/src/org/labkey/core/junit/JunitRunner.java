/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.TestContext;

import java.util.ArrayList;

/*
* User: adam
* Date: Jul 5, 2009
* Time: 10:54:59 AM
*/
public class JunitRunner
{
    private static final Logger LOG = Logger.getLogger(JunitRunner.class);

    public static class RunnerResult
    {
        Result junitResult = new Result();
        ArrayList<CPUTimer> perfResults = new ArrayList<>();
    }

    static RunnerResult run(Class clazz)
    {
        assert !TestCase.class.isAssignableFrom(clazz);

        LOG.info("Starting " + clazz.getName());

        try
        {
            TestContext.get().clearPerfResults();
            RunnerResult r = new RunnerResult();
            r.junitResult = JUnitCore.runClasses(clazz);
            if (r.junitResult.wasSuccessful())
                r.perfResults.addAll(TestContext.get().getPerfResults());
            return r;
        }
        finally
        {
            TestContext.get().clearPerfResults();
            LOG.info("Completed " + clazz.getName());
        }
    }
}
