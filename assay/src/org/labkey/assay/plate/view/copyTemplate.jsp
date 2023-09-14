<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.assay.plate.Plate" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.assay.PlateController.CopyTemplateBean" %>
<%@ page import="org.labkey.assay.PlateController.HandleCopyAction" %>
<%@ page import="org.labkey.assay.PlateController.PlateTemplateListAction" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<CopyTemplateBean> me = (JspView<CopyTemplateBean>) HttpView.currentView();
    CopyTemplateBean bean = me.getModelBean();
%>
<labkey:errors />
<table>
    <tr>
        <td>Copy <b><%= h(bean.getPlate().getName()) %></b> to:</td>
    </tr>
    <%=bean.getTreeHtml()%>
    <tr>
        <td>
            <br>
            <labkey:form action="<%=urlFor(HandleCopyAction.class)%>" method="POST">
                <input type="hidden" name="destination" value="<%= h(bean.getSelectedDestination()) %>">
                <input type="hidden" name="plateId" value="<%= bean.getPlate().getRowId() %>">
                <%= button("Cancel").href(PlateTemplateListAction.class, getContainer()) %>
                <%= bean.getSelectedDestination() != null ? button("Copy").submit(true) : button("Copy").submit(true).onClick("alert('Please select a destination folder.'); return false;") %>
            </labkey:form>
        </td>
    </tr>
<%
    List<? extends Plate> templates = bean.getDestinationTemplates();
    if (templates != null)
    {
%>
    <tr>
        <th align="left">Templates currently in <%= h(bean.getSelectedDestination()) %>:</th>
    </tr>
<%
        if (templates.isEmpty())
        {
%>
    <tr>
        <td class="labkey-indented">None</td>
    </tr>
<%
        }
        else
        {
            for (Plate template : templates)
            {
%>
    <tr>
        <td class="labkey-indented"><%= h(template.getName()) %></td>
    </tr>
<%
            }
        }
    }
%>
</table>
