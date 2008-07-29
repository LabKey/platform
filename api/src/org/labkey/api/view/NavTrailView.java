/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.Container;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.HelpTopic;
import static org.labkey.api.util.PageFlowUtil.filter;
import org.labkey.api.security.ACL;
import org.labkey.api.view.template.PageConfig;

import java.io.PrintWriter;
import java.util.*;


/**
 * User: mbellew
 * Date: Feb 10, 2004
 * Time: 9:24:46 AM
 */
public class NavTrailView extends HttpView
{
    private PageConfig _pageConfig = null;
    private String _title;
    private List<NavTree> _crumbTrail;
    private List<NavTree> tabs = new ArrayList<NavTree>();

    public NavTrailView(ViewContext context, String title, PageConfig pageConfig, List<NavTree> moduleChildren)
    {
        _title = title;
        _pageConfig = pageConfig;
        _crumbTrail = moduleChildren;

        computeTabs(context);
    }


    private static class Tab extends NavTree
    {
        public Tab(String name, String link, boolean selected)
        {
            super(name, link);
            // use expanded to mean selected
            setSelected(selected);
        }

        public Tab(ViewContext context, Module module, boolean selected)
        {
            super(module.getTabName(context), module.getTabURL(context.getContainer(), context.getUser()).getLocalURIString());
            setSelected(selected);
        }
    }


    private static List<Module> getSortedModuleList(final ViewContext context)
    {
        List<Module> sortedModuleList = new ArrayList();
        // special-case the portal module: we want it to always be at the far left.
        Module portal = null;
        for (Module module : ModuleLoader.getInstance().getModules())
        {
            if ("Portal".equals(module.getName()))
                portal = module;
            else
                sortedModuleList.add(module);
        }
        Collections.sort(sortedModuleList, new Comparator<Module>()
        {
            public int compare(Module moduleOne, Module moduleTwo)
            {
                return moduleOne.getName().compareToIgnoreCase(moduleTwo.getName());
            }
        });
        if (portal != null)
            sortedModuleList.add(0, portal);

        return sortedModuleList;
    }


    private void computeTabs(ViewContext context)
    {
        Container container = context.getContainer();

        FolderType folderType = container.getFolderType();
        if (!FolderType.NONE.equals(folderType))
        {
            tabs.add(new Tab(folderType.getLabel(), folderType.getStartURL(container, context.getUser()).getLocalURIString(), true));
        }
        else if (container.getActiveModules().size() == 1)
        {
            tabs.add(new Tab(context, container.getDefaultModule(), true));
        }
        else
        {
            String currentPageflow = context.getActionURL().getPageFlow();
            Set<Module> containerModules = container.getActiveModules();
            Module activeModule = ModuleLoader.getInstance().getModuleForPageFlow(currentPageflow);
            assert activeModule != null : "Pageflow '" + currentPageflow + "' is not claimed by any module.  " +
                    "This pageflow name must be added to the list of names returned by 'getPageFlowNameToClass' " +
                    "from at least one module.";
            List<Module> moduleList = getSortedModuleList(context);
            for (Module module : moduleList)
            {
                boolean selected = (module == activeModule);
                if (selected || containerModules.contains(module))
                {
                    Tab newTab = new Tab(context, module, selected);
                    tabs.add(newTab);
                }
            }
        }
    }


    private PrintWriter _out;
    private String _contextPath;    

    public void renderInternal(Object model, PrintWriter out)
            throws Exception
    {
        ViewContext context = getViewContext();

        _out = out;
        _contextPath = filter(context.getContextPath());

        String connectionsInUse = null;
        if (AppProps.getInstance().isDevMode())
        {
            int count = ConnectionWrapper.getActiveConnectionCount();
            if (count > 0)
            {
                connectionsInUse = count + " DB connection" + (count == 1 ? "" : "s") + " in use.";
                int leakCount = ConnectionWrapper.getProbableLeakCount();
                if (leakCount > 0)
                {
                    connectionsInUse += " " + leakCount + " probable leak" + (leakCount == 1 ? "" : "s") + ".";
                }
            }
        }

        //
        // TABSTRIP
        //

        _out.print("<table class=\"labkey-nav-bar\" id=\"navBar\" ");
        WebTheme theme = WebTheme.getTheme();
        if (tabs.size() == 1)
            _out.print("style=\"border-top: 1px solid #" + theme.getHeaderLineColor() + "\"");
        _out.print("><tr>\n");

        int tabHeight = ButtonServlet.getTabHeight();
        int lastTabHeight = tabHeight - ButtonServlet.getTabCornerHeight();

        if (tabs.size() > 1)
        {
            NavTree firstTab = tabs.get(0);
            NavTree lastTab = tabs.get(tabs.size() - 1);
            startTabstrip(firstTab);
            for (NavTree tab : tabs)
                drawTab(tab, tab==firstTab, tab==lastTab, lastTabHeight, tabHeight);
            endTabstrip(lastTab);
        }
        _out.print("<td id=\"labkey-end-nav-tab-space\"");
        if (tabs.size() > 1)
            _out.print(" class=\"labkey-nav-tab-space\"");
        _out.print("\">");

        if (null != connectionsInUse || _pageConfig.getExploratoryFeatures())
        {
            out.print("<span>");

            if (null != connectionsInUse)
            {
                _out.print(formatLink(connectionsInUse, ActionURL.toPathString("admin", "memTracker.view", "")));

                if (_pageConfig.getExploratoryFeatures())
                    _out.print("&nbsp;&nbsp;");
            }
            if (_pageConfig.getExploratoryFeatures())
            {
                _out.print("<a class=\"labkey-error\" href=\"");_out.print(filter((new HelpTopic("exploratory", HelpTopic.Area.CPAS)).getHelpTopicLink()));
                _out.print("\" target=\"help\">This page contains exploratory features</a>");
            }

            _out.print("</span>");
        }

        _out.print("</td>\n</tr></table>\n");

        //
        // CRUMB TRAIL
        //

        _out.print("<table class=\"labkey-nav-bar\">\n");
        boolean hasCrumbTrail = null != _crumbTrail && _crumbTrail.size() > 1;
        _out.print("<tr><td colspan=");
        _out.print(hasCrumbTrail ? "1" : "2");
        out.print(" class=\"labkey-crumb-trail\">");
        if (hasCrumbTrail)
        {
            _out.print("<span id=\"navTrailAncestors\">");
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
            drawAdminTd(context);
        }

        out.print("</tr>\n<tr><td colspan=");
        out.print(hasCrumbTrail ? "2" : "1");
        out.print(" class=\"labkey-nav-page-header\">");
        String lastCaption = _title;
        if (_crumbTrail.size() > 0)
            lastCaption = _crumbTrail.get(_crumbTrail.size()-1).getKey();
        if (lastCaption != null)
        {
            _out.print("<span id=\"labkey-nav-trail-current-page\" class=\"labkey-nav-page-header\">");_out.print(filter(_title));_out.print("</span>");
        }
        _out.print("</td>");
        if (!hasCrumbTrail)
            drawAdminTd(context);
        _out.print("</tr>\n</table>");
    }

    void drawAdminTd(ViewContext context) throws Exception
    {
        _out.print("<td align=right>");
        if (context.hasPermission(ACL.PERM_ADMIN))
            include(new PopupAdminView(context, _pageConfig));
        else
            _out.print("&nbsp;");
        _out.print("</td>");
    }

    void startTabstrip(NavTree firstTab)
    {
    }

    void drawTab(NavTree tab, boolean isFirst, boolean isLast, int lastTabHeight, int tabHeight)
    {
        String link = tab.getValue();
        String name = tab.getKey();
        boolean active = link != null;
        String className = tab.isSelected() ? "labkey-nav-tab-selected" : active ? "labkey-nav-tab" : "labkey-nav-tab-inactive";
        WebTheme theme = WebTheme.getTheme();
        _out.print("<td class=\"labkey-nav-tab-space\"><img width=\"5\" src=\"");
        _out.print(_contextPath);
        _out.print("/_.gif\"></td>");
        _out.print("<td><table><tr>");
        if (active)
        {
            _out.print("<td style=\"margin-bottom:0px\" class=");_out.print(className);_out.print(">");
            _out.print("<a href=\"");_out.print(filter(link));_out.print("\" id=\"");_out.print(tab.getKey());_out.print("Tab\">");
            _out.print(filter(name));_out.print("</a>");
        }
        else
        {
            _out.print("<td style=\"vertical-align:bottom;\" class=\"");_out.print(className);_out.print("\">");_out.print(filter(name));_out.print("</td>");
        }
        _out.print("</tr></table></td>\n");
    }

    void endTabstrip(NavTree lastTab)
    {
        _out.print("<td class=\"labkey-nav-tab-space\"" +
                " width=\"100%\"><img src=\"");
        _out.print(_contextPath);
        _out.print("/_.gif\"></td>\n");
    }

    private String formatLink(NavTree tree)
    {
        return formatLink(tree.getKey(), tree.getValue());
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
