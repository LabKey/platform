/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import org.labkey.api.util.PageFlowUtil;
import org.apache.log4j.Logger;
import org.labkey.api.util.TidyUtil;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * User: matthewb
 * Date: Jun 10, 2007
 * Time: 9:26:55 AM
 */
public class TidyFilterView extends HttpView
{
    boolean _xml = false;
    
    public TidyFilterView(boolean xml)
    {
        _xml = xml;
    }
    
    protected void renderInternal(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        ModelAndView mv = getBody();
        PageFlowUtil.Content c = PageFlowUtil.getViewContent(mv, request, response);

        ArrayList<String> errors = new ArrayList<>();
        
        String content;
        if (_xml)
            content = TidyUtil.tidyXML(c.content, errors);
        else
            content = TidyUtil.tidyHTML(c.content, false, errors);

        if (errors.size() > 0)
        {
            PrintWriter out = response.getWriter();
            for (String e : errors)
            {
                Logger.getLogger(TidyFilterView.class).warn(e);
                if (_xml)
                {
                    out.print("<error>");
                    out.print(PageFlowUtil.filterXML(e));
                    out.println("</error>");
                }
                else
                {
                    out.print("<span class=labkey-error>");
                    out.print(PageFlowUtil.filter(e));
                    out.print("</span><br>");
                }
            }
            out.println("</body></html>");
        }

        response.getWriter().print(content);
    }
}
