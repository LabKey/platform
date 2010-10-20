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

package org.labkey.api.util;

import java.io.PrintWriter;

class WebPartErrorRenderer extends ErrorRenderer
{
    WebPartErrorRenderer(int status, String message, Throwable x, boolean isStartupFailure)
    {
        super(status, message, x, isStartupFailure);
    }

    public void renderStart(PrintWriter out)
    {
        String id = "errorDiv" + System.identityHashCode(this);
        out.println("<div id='" + id + "' style=\"height:200px; overflow:scroll;\">");
        if (null != getHeading())
        {
            out.println("<h3 style=\"color:red;\">" + getHeading() + "</h3>");
        }
        out.println("<script>new Ext.Resizable(Ext.get('" + id + "'), { handles:'se', minWidth:200, minHeight:100, wrap:true, style:{padding:2}})</script>");
        super.renderStart(out);
    }

    public void renderEnd(PrintWriter out)
    {
        super.renderEnd(out);
        out.println("</div>");
    }
}
