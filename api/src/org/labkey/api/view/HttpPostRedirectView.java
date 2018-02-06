/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.api.view;

import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map;

/**
 * User: kevink
 * Date: 11/8/12
 *
 * Renders an HTML form that will POST inputs to a URL.
 * Set the PageConfig template to Template.None before rendering the view.
 *
 * @see HttpRedirectView
 */
public class HttpPostRedirectView extends HttpView
{
    final String _url;
    final Collection<? extends Map.Entry<String, String>> _hiddenInputs;

    public HttpPostRedirectView(String url, Map<String, String> hiddenInputs)
    {
        _url = url;
        _hiddenInputs = hiddenInputs.entrySet();
    }

    public HttpPostRedirectView(String url, Collection<Pair<String, String>> hiddenInputs)
    {
        _url = url;
        _hiddenInputs = hiddenInputs;
    }

    @Override
    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
        out.println("<html>");
        out.println("<body onload='document.forms[\"form\"].submit()'>");
        out.println("<form name='form' method='POST' action='" + PageFlowUtil.filter(_url) + "'>");
        out.println("<input type=hidden name='X-LABKEY-CSRF' value='" + CSRFUtil.getExpectedToken(getViewContext()) + "'>");
        for (Map.Entry<String, String> pair : _hiddenInputs)
        {
            out.println("<input type='hidden' name='" + PageFlowUtil.filter(pair.getKey()) + "' value='" + PageFlowUtil.filter(pair.getValue()) + "'>");
        }
        out.println("</form>");
        out.println("</body>");
        out.println("</html>");
    }
}
