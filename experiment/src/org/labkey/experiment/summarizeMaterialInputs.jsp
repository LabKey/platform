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
<%@ page import="org.labkey.api.exp.api.ExpMaterial" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.labkey.experiment.DerivedSamplePropertyHelper" %>
<%@ page import="org.labkey.api.exp.PropertyDescriptor" %>
<%@ page import="org.labkey.api.data.TableViewForm" %>
<%@ page import="org.labkey.api.data.DisplayColumnGroup" %>
<%@ page import="org.labkey.api.exp.property.DomainProperty" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib"%>
<%
    JspView<ExperimentController.DeriveSamplesChooseTargetBean> me = (JspView<ExperimentController.DeriveSamplesChooseTargetBean>) HttpView.currentView();
    ExperimentController.DeriveSamplesChooseTargetBean bean = me.getModelBean();
    List<ExpMaterial> sameTypeInputs = new ArrayList<>();
    DerivedSamplePropertyHelper helper = bean.getPropertyHelper();
    if (helper.getSampleSet() != null)
    {
        for (ExpMaterial material : bean.getSourceMaterials().keySet())
        {
            if (helper.getSampleSet().equals(material.getSampleSet()))
            {
                sameTypeInputs.add(material);
            }
        }
    }
%>

<table>
    <tr>
        <td valign="bottom" class="labkey-form-label"><strong>Sample Name</strong></td>
        <td valign="bottom" class="labkey-form-label"><strong>Role</strong><%= PageFlowUtil.helpPopup("Role", "Roles allow you to label an input as being used in a particular way. It serves to disambiguate the purpose of each of the input materials. Each input should have a unique role.")%></td>
        <% if (!sameTypeInputs.isEmpty()) { %>
            <td valign="bottom" class="labkey-form-label"><strong>Copy properties to...</strong></td>
        <% } %>
    </tr>
<%
    for (Map.Entry<ExpMaterial, String> entry : bean.getSourceMaterials().entrySet())
    { %>
        <tr>
            <td><%= h(entry.getKey().getName())%></td>
            <td><%= h(entry.getValue()) %></td>
            <% if (sameTypeInputs.contains(entry.getKey())) { %>
                <td>
                    <%
                    String separator = "";
                    Map<DomainProperty, DisplayColumnGroup> groups = helper.getGroups();
                    for (int i = 0; i < helper.getSampleNames().size(); i++)
                    {
                        %><%= separator %>
                        <a href="#" onclick="{<%
                        for (Map.Entry<PropertyDescriptor, Object> propEntry : entry.getKey().getPropertyValues().entrySet())
                        {
                            DisplayColumnGroup group = groups.get(propEntry.getKey());
                            if (group != null && group.isCopyable())
                            {
                                %>document.getElementsByName('<%= group.getColumns().get(i).getColumnInfo().getPropertyName() %>')[0].value = '<%= h(propEntry.getValue()) %>';
                                if (document.getElementsByName('<%= group.getColumns().get(i).getColumnInfo().getPropertyName() %>')[0].onchange != null)
                                {
                                    document.getElementsByName('<%= group.getColumns().get(i).getColumnInfo().getPropertyName() %>')[0].onchange();
                                } <%
                            }
                        }
                        %>return false; }"><%= h(helper.getSampleNames().get(i)) %></a><%
                        separator = ",";
                    } %>
                </td>
            <% } %>
        </tr>
    <%
    } %>
</table>
