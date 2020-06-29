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
<%@ page import="org.labkey.api.data.DataRegionSelection" %>
<%@ page import="org.labkey.api.exp.api.ExpMaterial" %>
<%@ page import="org.labkey.api.exp.api.ExpSampleType" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib"%>
<%
    JspView<ExperimentController.DeriveSamplesChooseTargetBean> me = (JspView<ExperimentController.DeriveSamplesChooseTargetBean>) HttpView.currentView();
    ExperimentController.DeriveSamplesChooseTargetBean bean = me.getModelBean();

    Map<Integer, String> sampleTypeOptions = new LinkedHashMap<>();
    sampleTypeOptions.put(0, "Not a member of a sample type");
    for (ExpSampleType st : bean.getSampleTypes())
    {
        sampleTypeOptions.put(st.getRowId(), st.getName() + " in " + st.getContainer().getPath());
    }
%>

<labkey:form action="<%=h(buildURL(ExperimentController.DeriveSamplesAction.class))%>" method="get">
    <% if (bean.getDataRegionSelectionKey() != null) { %>
    <input type="hidden" name="<%= h(DataRegionSelection.DATA_REGION_SELECTION_KEY) %>" value="<%=h(bean.getDataRegionSelectionKey())%>"/>
    <% } %>

    <table class="lk-fields-table">
        <tr>
            <td class="labkey-form-label">Source materials:</td>
            <td>
                <table class="labkey-data-region-legacy labkey-show-borders">
                    <tr>
                        <td class="labkey-column-header">Name</td>
                        <td class="labkey-column-header">Role<%= helpPopup("Role", "Roles allow you to label an input as being used in a particular way. It serves to disambiguate the purpose of each of the input materials. Each input should have a unique role.")%></td>
                    </tr>
                <%
                int roleIndex = 0;
                for (ExpMaterial material : bean.getSourceMaterials().keySet())
                { %>
                    <tr class="<%=h(roleIndex % 2 == 0 ? "labkey-alternate-row" : "labkey-row")%>">
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
            <td class="labkey-form-label">Target sample type:</td>
            <td colspan="2">
                <labkey:select name="targetSampleTypeId">
                    <labkey:options value="<%=bean.getTargetSampleTypeId()%>" map="<%=sampleTypeOptions%>"/>
                </labkey:select>
            </td>
        </tr>
        <tr>
            <td></td>
            <td><labkey:button text="Next" /></td>
        </tr>
    </table>
</labkey:form>
