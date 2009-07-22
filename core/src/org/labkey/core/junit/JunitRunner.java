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

package org.labkey.core.junit;

import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.Test;

import java.lang.reflect.Method;

/*
* User: adam
* Date: Jul 5, 2009
* Time: 10:54:59 AM
*/
public class JunitRunner
{
    static void run(Class<? extends TestCase> testCase, TestResult result) throws IllegalAccessException, InstantiationException
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

    
}
