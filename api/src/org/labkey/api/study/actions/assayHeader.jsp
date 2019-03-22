<%
/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.study.actions.AssayHeaderView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.PopupMenuView" %>
<%@ page import="org.labkey.api.view.PopupMenu" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    JspView<AssayHeaderView> me = (JspView<AssayHeaderView>) HttpView.currentView();
    AssayHeaderView bean = me.getModelBean();
    if (bean.isIncludeDescription() && bean.getProtocol().getProtocolDescription() != null && !"".equals(bean.getProtocol().getProtocolDescription().trim())) { %>
        <p><%= h(bean.getProtocol().getProtocolDescription()) %></p>

<% }
    ActionURL current = getActionURL();
    for (NavTree link : bean.getLinks())
    {
        if (link.getChildCount() == 0)
        {
            String url = link.getHref();
            boolean active = current.getLocalURIString().equals(url);
%>
            <%= text(active ? "<strong>" : "") %><%= textLink(link.getText(), url) %><%= text(active ? "</strong>" : "") %>
<%
        }
        else
        {
            PopupMenuView popup = new PopupMenuView(link);
            popup.setButtonStyle(PopupMenu.ButtonStyle.TEXTBUTTON);
            me.include(popup, out);
        }
    }
%>
