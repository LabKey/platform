/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.core;

import org.junit.Assert;
import org.junit.Test;

// Regression test for Issue #51784. When running Tomcat 10.1.33 along with the Datadog Java agent, attempting to load
// by name a class like InstrumentSystemThread failed with a class loading exception.
public class ClassLoaderTestCase extends Assert
{
    @Test
    public void testLoadingInnerClassByName() throws ClassNotFoundException
    {
        Class.forName("org.labkey.core.SystemThread$InstrumentSystemThread");
    }
}
