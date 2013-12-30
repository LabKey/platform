<%
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
%>
<%@ page import="org.labkey.api.study.actions.AssayHeaderView" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
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
