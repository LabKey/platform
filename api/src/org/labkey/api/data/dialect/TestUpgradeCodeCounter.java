/*
 * Copyright (c) 2010-2026 LabKey Corporation
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

package org.labkey.api.data.dialect;

/** Counter used by Java-based module upgrade method testing */
public class TestUpgradeCodeCounter
{
    private static int _counter = 0;

    private TestUpgradeCodeCounter()
    {
    }

    public static void resetCounter()
    {
        _counter = 0;
    }

    public static void incrementCounter()
    {
        _counter++;
    }

    public static int getCount()
    {
        return _counter;
    }
}
