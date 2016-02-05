/*
 * Copyright (c) 2005-2016 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.view.template.PageConfig;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static org.labkey.api.util.PageFlowUtil.filter;

/**
 * User: mbellew
 * Date: Feb 10, 2004
 * Time: 9:24:46 AM
 */
public class NavTrailView extends HttpView
{
    private String _title;
    private List<NavTree> _crumbTrail;
    private List<NavTree> tabs = new ArrayList<>();

    public NavTrailView(ViewContext context, String title, PageConfig pageConfig, List<NavTree> moduleChildren)
    {
        _title = title;
        _crumbTrail = moduleChildren;
    }


    private PrintWriter _out;
    private String _contextPath;    

    public void renderInternal(Object model, PrintWriter out)
            throws Exception
    {
        ViewContext context = getViewContext();

        _out = out;
        _contextPath = filter(context.getContextPath());

        //
        // TABSTRIP
        //

        _out.print("<table id=\"navBar\" class=\"labkey-tab-strip");
        if (tabs.size() == 1)
            _out.print(" labkey-nav-bordered\" style=\"border-right:0;border-bottom:0;border-left:0;");
        _out.print("\"><tr>\n");

        if (tabs.size() > 1)
        {
            for (NavTree tab : tabs)
                drawTab(tab);
            endTabstrip();
        }
        _out.print("<td id=\"labkey-end-tab-space\"");
        if (tabs.size() > 1)
            _out.print(" class=\"labkey-tab-space\"");
        _out.print(">");

        _out.print("</td>\n</tr></table>\n");

        //
        // CRUMB TRAIL
        //

        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(context.getContainer());
        _out.print("<table class=\"labkey-nav-trail\">\n");
        boolean hasCrumbTrail = null != _crumbTrail && _crumbTrail.size() > 1;
        _out.print("<tr><td colspan=");
        _out.print(hasCrumbTrail ? "1" : "2");
        out.print(" class=\"labkey-crumb-trail\">");
        if (hasCrumbTrail)
        {
            _out.print("<span id=\"navTrailAncestors\" style=\"visibility:hidden\">");
            String and = "";
            for (int i=0 ; i<_crumbTrail.size()-1; i++)
            {
                NavTree childLink = _crumbTrail.get(i);
                out.print(and);
                out.print(formatLink(childLink));
                and = "&nbsp;&gt;&nbsp;";
            }
            _out.print(and);
            _out.print("</span></td>");
        }

        out.print("</tr>\n<tr><td colspan=");
        out.print(hasCrumbTrail ? "2" : "1");
        out.print(" class=\"labkey-nav-page-header-container\">");
        String lastCaption = _title;
        if (_crumbTrail.size() > 0)
            lastCaption = _crumbTrail.get(_crumbTrail.size()-1).getText();
        if (lastCaption != null)
        {
            _out.print("<span class=\"labkey-nav-page-header\" id=\"labkey-nav-trail-current-page\" style=\"visibility:hidden\">");_out.print(filter(_title));_out.print("</span>");
        }
        _out.print("</td>");
        _out.print("</tr>\n</table>");
    }

    void drawTab(NavTree tab)
    {
        String link = tab.getHref();
        String name = tab.getText();
        boolean active = link != null;
        String className = tab.isSelected() ? "labkey-tab-selected" : active ? "labkey-tab" : "labkey-tab-inactive";
        _out.print("<td class=\"labkey-tab-space\"><img width=\"5\" src=\"");
        _out.print(_contextPath);
        _out.print("/_.gif\"></td>");
        if (active)
        {
            _out.print("<td style=\"margin-bottom:0px\" class=");_out.print(className);_out.print(">");
            _out.print("<a href=\"");_out.print(filter(link));_out.print("\" id=\"");_out.print(tab.getText());_out.print("Tab\">");
            _out.print(filter(name));_out.print("</a></td>");
        }
        else
        {
            _out.print("<td style=\"vertical-align:bottom;\" class=\"");_out.print(className);_out.print("\">");_out.print(filter(name));_out.print("</td>");
        }
    }

    void endTabstrip()
    {
        _out.print("<td class=\"labkey-tab-space\"" +
                " width=\"100%\"><img src=\"");
        _out.print(_contextPath);
        _out.print("/_.gif\"></td>\n");
    }

    private String formatLink(NavTree tree)
    {
        return formatLink(tree.getText(), tree.getHref());
    }

    private String formatLink(String display, String href)
    {
        return formatLink(display, href, null);
    }

    private String formatLink(String display, String href, String script)
    {
        if (null == display)
            display = href;
        if (href == null && script != null)
            href = "#";
        if (href == null && display == null)
            return "";

        if (href == null)
            return filter(display);
        else
        {
            StringBuilder sb = new StringBuilder();
            sb.append("<a href=\"").append(filter(href)).append("\"");
            if (script != null)
                sb.append(" onclick=\"").append(filter(script)).append("\"");
            sb.append(">").append(filter(display)).append("</a>");
            return sb.toString();
        }
    }
}
