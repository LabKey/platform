/*
 * Copyright (c) 2019 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.HasHtmlString;
import org.labkey.api.util.HtmlString;

import javax.servlet.jsp.JspWriter;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class LabKeyJspWriter extends JspWriterWrapper
{
    private static final Logger LOG = Logger.getLogger(LabKeyJspWriter.class);
    private static final Logger LOGSTRING = Logger.getLogger(LabKeyJspWriter.class.getName() + ".string");
    private static final AtomicInteger STRING_INVOCATIONS = new AtomicInteger();
    private static final Set<String> UNIQUE_STRING_INVOCATIONS = new ConcurrentHashSet<>();

    LabKeyJspWriter(JspWriter jspWriter)
    {
        super(jspWriter);
    }

    @Override
    public void print(char[] s) throws IOException
    {
        throw new IllegalStateException("A JSP is attempting to render a character array!");
    }

    @Override
    public void print(String s) throws IOException
    {
        STRING_INVOCATIONS.incrementAndGet();
        if (UNIQUE_STRING_INVOCATIONS.add(Thread.currentThread().getStackTrace()[2].toString()))
        {
            LOGSTRING.info(" A JSP is printing a string!", new Throwable());
        }

        super.print(s);
    }

    @Override
    public void print(Object obj) throws IOException
    {
        if (!(obj instanceof HtmlString))
        {
            if (obj instanceof HasHtmlString)
            {
                obj = ((HasHtmlString) obj).getHtmlString();
            }
            // Allow Number and Boolean for convenience -- no encoding needed for those. Also allow null, which is rendered
            // as "null" (useful when generating JavaScript).
            else if (null != obj && !(obj instanceof Number) && !(obj instanceof Boolean))
            {
                throw new IllegalStateException("A JSP is attempting to render an object of class " + obj.getClass().getName() + "!");
            }
        }

        super.print(obj);
    }

    public static void logStatistics()
    {
        if (AppProps.getInstance().isDevMode())
        {
            LOG.info("print(String) invocations: " + STRING_INVOCATIONS);
            LOG.info("Unique code points that invoke print(String): " + UNIQUE_STRING_INVOCATIONS.size());
        }
    }
}
