<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.study.PlateTemplate" %>
<%@ page import="org.labkey.api.study.WellGroup" %>
<%@ page import="org.labkey.api.study.WellGroupTemplate" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.plate.PlateController" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<PlateController.TemplateViewBean> me = (JspView<PlateController.TemplateViewBean>) HttpView.currentView();
    PlateController.TemplateViewBean bean = me.getModelBean();
    PlateTemplate template = bean.getTemplate();
%>
<labkey:form action="plateTemplate.view" method="GET">
    <input type="hidden" name="name" value="<%= h(template.getName()) %>">
    <select name="type" onChange="form.submit();">
        <%
            for (WellGroup.Type type : WellGroup.Type.values())
            {
        %>
        <option value="<%=h(type.name())%>"<%=selected(type == bean.getType())%>><%= h(type.name()) %></option>
        <%
            }
        %>
    </select>
</labkey:form>
<table>
    <tr>
        <td>&nbsp;</td>
        <%
            for (int col = 0; col < template.getColumns(); col++)
            {
        %>
        <td><center><b><%= col + 1 %></b></center></td>
        <%
            }
        %>
    </tr>
<%
    Map<Integer, String> groupToColor = new HashMap<>();
    char rowChar = 'A';
    for (int row = 0; row < template.getRows(); row++)
    {
%>
        <tr>
            <td><b><%= rowChar %></b></td>
        <%
            for (int col = 0; col < template.getColumns(); col++)
            {
                List<? extends WellGroupTemplate> groups = template.getWellGroups(template.getPosition(row, col));
                StringBuilder wellDisplayString = new StringBuilder();
                TreeMap<String, String> currentGroupColors = new TreeMap<>();
                for (WellGroupTemplate group : groups)
                {
                    if (bean.getType() == null || bean.getType() == group.getType())
                    {
                        String color = groupToColor.get(group.getRowId());
                        if (color == null)
                        {
                            color = me.getModelBean().getColorGenerator().next();
                            groupToColor.put(group.getRowId(), color);
                        }
                        currentGroupColors.put(group.getName(), color);
                    }
                }

        %>
            <td class="labkey-bordered">
                <table>
                    <%
                        if (currentGroupColors.isEmpty())
                        {
                    %>
                            <td>Non-<%= h(bean.getType().name()) %></td>
                    <%
                        }
                        else
                        {
                            for (Map.Entry<String, String> groupColor : currentGroupColors.entrySet())
                            {
                    %>
                            <td bgcolor="<%= groupColor.getValue() %>"><%= h(groupColor.getKey()) %><br></td>
                    <%
                            }
                        }
                    %>
                </table>
            </td>
        <%
        }
        %>
        </tr>
        <%
        rowChar++;
    }
%>
</table>