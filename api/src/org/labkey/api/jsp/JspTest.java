/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.jsp;

import org.labkey.api.util.TestContext;

/**
 * Base class for junit tests implemented in a JSP
 */
public abstract class JspTest extends JspContext
{
    protected final TestContext testContext;

    protected JspTest()
    {
        super();
        testContext = TestContext.get();
    }

    public static abstract class DRT extends JspTest
    {
    }

    public static abstract class BVT extends JspTest
    {
    }

    public static abstract class DAILY extends JspTest
    {
    }

    public static abstract class WEEKLY extends JspTest
    {
    }

    public static abstract class PERFORMANCE extends JspTest
    {
    }
}
