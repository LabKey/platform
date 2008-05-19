<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.study.actions.AssayHeaderView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.view.PopupMenu" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AssayHeaderView> me = (JspView<AssayHeaderView>) HttpView.currentView();
    AssayHeaderView bean = me.getModelBean();
%>
<table class="normal" cellspacing="0" width="100%">
    <tr>
        <td>
            <p><%= h(bean.getProtocol().getProtocolDescription()) %></p>
            <%
                if (bean.showProjectAdminLink())
                {
                    Container assayContainer = bean.getProtocol().getContainer();
                    ActionURL assayLink = getViewContext().cloneActionURL();
                    assayLink.setExtraPath(assayContainer.getPath());
                    assayLink.setPageFlow("assay");
            %>
            <p>This assay design is defined in folder <b><%= h(assayContainer.getPath())%></b>.  To manage this assay design, you must
                <%= textLink("view assay in definition folder", assayLink)%>.</p>
            <%
                }
            %>
            <p>
                <%
                    if (bean.getManagePopupView() != null)
                    {
                        me.include(bean.getManagePopupView(), out);
                    }
                    for (Map.Entry<String, ActionURL> entry : bean.getLinks().entrySet())
                    {
                        ActionURL current = getViewContext().getActionURL();
                        ActionURL link = entry.getValue();
                        boolean active = current.getLocalURIString().equals(link.getLocalURIString()); %>
                        <%= active ? "<strong>" : "" %><%= textLink(entry.getKey(), link) %><%= active ? "</strong>" : "" %>
                <% } %>
            </p>
        </td>
    </tr>
</table>
