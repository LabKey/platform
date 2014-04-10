/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.api.util;

import org.apache.commons.lang3.math.NumberUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * User: adam
 * Date: 3/26/2014
 * Time: 9:31 AM
 */
public class NumberUtilsLabKey
{
    /*
        This wrapper method exists because the 3.3.1 version of isNumber() had a huge bug, https://issues.apache.org/jira/browse/LANG-992
        This has supposedly been fixed, but we're using a chokepoint to test the new code and allow for quick revert if needed.

        We'll remove this method once we've confirmed the fix, though we might as well leave the junit test in place.
    */
    public static boolean isNumber(String str)
    {
        return NumberUtils.isNumber(str);
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testNumbers()
        {
            numbers("1", "-1", "23", "123456", "-123456", "0.4790", "-0.4790", ".4790", "-.4790", "-9.156013e-002");
            notNumbers(null, "", "   ", "\n", "\n", "-0.4790X", "123ABC", "-123ABC", "0.4790B", "0xABQCD");
        }

        private void numbers(String... strings)
        {
            for (String string : strings)
                assertTrue("isNumber(\"" + string + "\" should have returned true!", isNumber(string));
        }

        private void notNumbers(String... strings)
        {
            for (String string : strings)
                assertFalse("isNumber(\"" + string + "\" should have returned false!", isNumber(string));
        }
    }
}
