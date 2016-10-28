/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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
import java.util.HashMap;
import java.util.Map;

/** Lays out child {@link ModelAndView} horizontally */
public class HBox extends VBox
{
    private Map<ModelAndView, String> _widths = new HashMap<>();
    private String _tableWidth="100%";

    public HBox(ModelAndView... views)
    {
        super(views);
    }

    public void setTableWidth(String width)
    {
        _tableWidth = width;
    }

    public void addView(ModelAndView v, String width)
    {
        super.addView(v);
        _widths.put(v, width);
    }

    @Override
    public void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        PrintWriter out = response.getWriter();

        if (_views != null && !_views.isEmpty())
        {
            out.write("<table width=\"" + _tableWidth + "\"><tr>");
            for (ModelAndView view : _views)
            {
                if (null == view)
                    continue;
                String width = _widths.get(view);
                out.write("<td valign=top align=left" + (width != null ? " width=" + width : "") + ">");
                include(view);
                out.write("</td>");
            }
            out.write("</tr></table>");
        }
    }
}
