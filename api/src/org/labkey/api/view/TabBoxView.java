/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * User: matthewb
 * Date: May 20, 2009
 * Time: 2:13:22 PM
 *
 * like VBox or HBox, but with tabs!
 */
public class TabBoxView extends VBox
{
    FrameType _frameType = FrameType.NONE;

    public void setFrameType(FrameType type)
    {

    }

    @Override
    public void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        PrintWriter out = response.getWriter();

        JspView tv = new JspView<>(TabBoxView.class, "tabbox.jsp", this);
        include(tv, out);

        out.println("<div style='display:none;'>");
        int index=0;
        for (ModelAndView view : _views)
        {
            if (null == view)
                continue;
            if (null != _frameType && view instanceof WebPartView)
                ((WebPartView)view).setFrame(_frameType);
            out.print("<div id='tabWebPart" + index + "'>");
            include(view);
            out.println("</div>");
            index++;
        }
        out.println("'div'");
    }
}
