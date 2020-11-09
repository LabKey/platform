/*
 * Copyright (c) 2010-2014 LabKey Corporation
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

import org.apache.jasper.runtime.HttpJspBase;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.MemTracker;
import org.labkey.api.view.HttpView;

import javax.servlet.jsp.JspWriter;

/**
 * User: adam
 * Date: Aug 10, 2010
 * Time: 4:05:51 PM
 */

// Simple base class for JSP templates that aren't rendering HTML (see JspTemplate)
public abstract class JspContext extends HttpJspBase
{
    protected JspContext()
    {
        MemTracker.getInstance().put(this);
    }

    @Override
    public void jspInit()
    {
    }

    @Override
    public void jspDestroy()
    {
    }

    public Object getModelBean()
    {
        return HttpView.currentModel();
    }

    /**
     * This is called by every JSP to get our standard JspWriter. In production mode, this is a pass-through; in dev mode,
     * it wraps the standard JspWriter with LabKeyJspWriter, an implementation that protects against unsafe output.
     */
    protected JspWriter getLabKeyJspWriter(JspWriter out)
    {
        return AppProps.getInstance().isDevMode() ? new LabKeyJspWriter(out) : out;
    }

    /**
     * Call this to allow String and unsafe Object output in development mode, undoing the call above. Typically, this
     * would be the first line of code in a JSP that generates non-HTML content:<br><br>
     *     {@code out = getPermissiveJspWriter(out);}
     * @param out Current JspWriter
     * @return A JspWriter that doesn't throw exceptions when rendering Strings and unsafe Objects
     */
    protected JspWriter getUnsafeJspWriter(JspWriter out)
    {
        return out instanceof LabKeyJspWriter ? ((LabKeyJspWriter) out).getWrappedJspWriter() : out;
    }
}
