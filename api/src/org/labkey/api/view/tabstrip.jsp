<%
/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.TabStripView"%>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.springframework.web.servlet.ModelAndView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<TabStripView> me = (JspView<TabStripView>) HttpView.currentView();
    TabStripView view = me.getModelBean();

    List<NavTree> tabs = view.getTabList();
    String tabId = view.getSelectedTabId();
%>
<table class="labkey-tab-strip">
    <tr>
    <%
        for (NavTree tab : tabs)
        {
            %>
            <td class="labkey-tab-space"><img src="<%=request.getContextPath()%>/_.gif" width="3"></td>
            <td id="<%=view._prefix%>tab<%=tab.getId()%>" class="<%=(tab.getId().equals(tabId) ? "labkey-tab-selected" : "labkey-tab")%>"><%

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
            %></td><%
        }
    %>
        <td class="labkey-tab-space" style="text-align:right;" width=100%>
            <img src="<%=request.getContextPath()%>/_.gif" height=1 width=5>
        </td>
    </tr>
    <tr>
        <td colspan="<%=tabs.size() * 2 + 2%>" class="labkey-tab-content" style="border-top:none;text-align:left;" width=100%>
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
%></div></td></tr></table>

<%-- enable changing selected tab in place --%>
<script type="text/javascript">

function selectTab(el)
{
    var tr;
    var tdSelected;
    while (el != document && !tr && !td)
    {
        if (!tdSelected && "TD" == el.tagName)
            tdSelected = el;
        if (!tr && "TR" == el.tagName)
            tr = el;
        el =  el.parentNode;
    }

    // disable the other tabs
    var tds = tr.getElementsByTagName("TD");
    for (var i=0 ; i<tds.length ; i++)
    {
        var td = tds[i];
        if (!td.id || -1 == td.id.indexOf("tab"))
            continue;
        if (td.className !=  "labkey-tab-selected" && td.className != "labkey-tab")
            continue;
        td.className =  td == tdSelected ? "labkey-tab-selected" : "labkey-tab";
    }
}
</script>

