<%
/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
%>
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.TabStripView" %>
<%@ page import="org.springframework.web.servlet.ModelAndView" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<TabStripView> me = (JspView<TabStripView>) HttpView.currentView();
    TabStripView view = me.getModelBean();

    List<NavTree> tabs = view.getTabList();
    String tabId = view.getSelectedTabId();
%>

<div>
    <ul class="labkey-tab-strip">
    <%
        for (NavTree tab : tabs)
        {
            %>
            <li class="<%=(tab.getId().equals(tabId) ? "labkey-tab-active" : "labkey-tab-inactive")%>" id="<%=view._prefix%>tab<%=tab.getId()%>"><%

            if (tab.getScript() == null && tab.getValue() == null)
            {
                %><%=h(tab.getKey())%><%
            }
            else if (tab.getScript() == null)
            {
                %><a href="<%=h(tab.getValue())%>"><%=h(tab.getKey())%>&nbsp;</a><%
            }
            else
            {
                String href = StringUtils.defaultString(tab.getValue(), "javascript:void(0);");
                %><a href="<%=h(href)%>" onclick="<%=h(tab.getScript())%>"><%=h(tab.getKey())%>&nbsp;</a><%
            }
            %></li><%
        }
    %>
        <div class="x-clear"></div>
    </ul>
    <div class="labkey-tab-strip-spacer"></div>
    <div class="labkey-tab-strip-content">
        <div id="<%=view._prefix%>tabContent"><%
            ModelAndView tabView = view.getTabView(tabId);
            if (tabView != null)
            {
                include(tabView, out);
            }
            else
            {
                %>No handler for view: <%=h(tabId)%><%
            }
        %></div>
    </div>
</div>
