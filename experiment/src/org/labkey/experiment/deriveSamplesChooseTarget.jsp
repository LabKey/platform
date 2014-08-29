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
<%@ page import="org.labkey.api.exp.api.ExpMaterial" %>
<%@ page import="org.labkey.api.exp.api.ExpSampleSet" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib"%>
<%
    JspView<ExperimentController.DeriveSamplesChooseTargetBean> me = (JspView<ExperimentController.DeriveSamplesChooseTargetBean>) HttpView.currentView();
    ExperimentController.DeriveSamplesChooseTargetBean bean = me.getModelBean();
%>

<labkey:form action="<%=h(buildURL(ExperimentController.DescribeDerivedSamplesAction.class))%>" method="get">

    <table>
        <tr>
            <td class="labkey-form-label">Source materials:</td>
            <td>
                <table>
                    <tr>
                        <td valign="bottom" class="labkey-form-label"><strong>Name</strong></td>
                        <td valign="bottom" class="labkey-form-label"><strong>Role</strong><%= PageFlowUtil.helpPopup("Role", "Roles allow you to label an input as being used in a particular way. It serves to disambiguate the purpose of each of the input materials. Each input should have a unique role.")%></td>
                    </tr>
                <%
                int roleIndex = 0;
                for (ExpMaterial material : bean.getSourceMaterials().keySet())
                { %>
                    <tr>
                        <td><input type="hidden" name="rowIds" value="<%= material.getRowId()%>" /><%= h(material.getName())%></td>
                        <td><select name="inputRole<%= roleIndex %>" onchange="document.getElementById('customRole<%= roleIndex %>').disabled = this.value != '<%= ExperimentController.DeriveSamplesChooseTargetBean.CUSTOM_ROLE %>';">
                            <option value=""></option>
                            <% for (String inputRole : bean.getInputRoles())
                            { %>
                                <option value="<%= h(inputRole)%>"><%= h(inputRole) %></option>
                            <% } %>
                            <option value="<%= ExperimentController.DeriveSamplesChooseTargetBean.CUSTOM_ROLE %>">Add a new role...</option>
                        </select> <input name="customRole<%= roleIndex %>" disabled="true" id="customRole<%= roleIndex %>"/></td>
                    </tr>
                <%
                    roleIndex++;
                }
                %>
                </table>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Number of derived samples:</td>
            <td colspan="2">
                <select name="outputCount">
                    <% for (int i = 1; i <= 20; i++)
                    { %>
                        <option <% if (bean.getSampleCount() == i) { %>selected<% } %> value="<%= i %>"><%= i %></option>
                    <% } %>
                </select>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Target sample set:</td>
            <td colspan="2">
                <select name="targetSampleSetId">
                    <option value="0">Not a member of a sample set</option>
                    <%
                    for (ExpSampleSet ss : bean.getSampleSets())
                    { %>
                        <option value="<%= ss.getRowId() %>"><%= h(ss.getName())%> in <%= h(ss.getContainer().getPath()) %></option>
                    <% } %>
                </select>
            </td>
        </tr>
        <tr>
            <td></td>
            <td><labkey:button text="Next" /></td>
        </tr>
    </table>
</labkey:form>