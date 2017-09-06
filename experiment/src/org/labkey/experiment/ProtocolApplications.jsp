<%
/*
 * Copyright (c) 2005-2017 LabKey Corporation
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
<%@ page import="org.json.JSONObject"%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.exp.ExperimentDataHandler" %>
<%@ page import="org.labkey.api.exp.api.ExpData" %>
<%@ page import="org.labkey.api.exp.api.ExpDataRunInput" %>
<%@ page import="org.labkey.api.exp.api.ExpMaterial" %>
<%@ page import="org.labkey.api.exp.api.ExpMaterialRunInput" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocolApplication" %>
<%@ page import="org.labkey.api.exp.api.ExpRun" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.api.ExpDataRunInputImpl" %>
<%@ page import="org.labkey.experiment.api.ExpMaterialRunInputImpl" %>
<%@ page import="org.labkey.experiment.api.ExpProtocolApplicationImpl" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Objects" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ExpRun> me = (JspView<ExpRun>) HttpView.currentView();
    ExpRun run = me.getModelBean();
    Container c = getContainer();
    int rowCount = 0;

    boolean debug = getViewContext().getRequest().getParameter("_debug") != null;
%>

<% if (debug) { %>
<table class="labkey-protocol-applications labkey-data-region-legacy labkey-show-borders">
    <tr>
        <td class="labkey-column-header">Step</td>
        <td class="labkey-column-header">Name</td>
        <td class="labkey-column-header">Type</td>
        <td class="labkey-column-header">Protocol</td>
        <td class="labkey-column-header">Date</td>
        <td class="labkey-column-header">Start</td>
        <td class="labkey-column-header">End</td>
        <td class="labkey-column-header">Records</td>
        <td class="labkey-column-header">Properties</td>
    </tr>
    <% for (ExpProtocolApplication protocolApplication : run.getProtocolApplications())
    {
        rowCount++;

        Map<String, Object> props = ((ExpProtocolApplicationImpl)protocolApplication).getProperties();
        List<? extends ExpMaterialRunInput> materialRunInputs = protocolApplication.getMaterialInputs();
        List<? extends ExpDataRunInput> dataRunInputs = protocolApplication.getDataInputs();
        List<? extends ExpMaterialRunInput> materialRunOutputs = protocolApplication.getMaterialOutputs();
        List<? extends ExpDataRunInput> dataRunOutputs = protocolApplication.getDataOutputs();
    %>
        <tr class="<%=text(rowCount%2==0 ? "labkey-row" : "labkey-alternate-row")%>">
            <td valign="top">
                <%=protocolApplication.getActionSequence()%>
            </td>
            <td valign="top">
                <a href="<%=h(ExperimentController.getShowApplicationURL(c, protocolApplication.getRowId()))%>"><%= h(protocolApplication.getName()) %></a>
            </td>
            <td valign="top"><%=h(protocolApplication.getApplicationType().name())%></td>
            <td valign="top">
                <a href="<%=ExperimentController.ExperimentUrlsImpl.get().getProtocolDetailsURL(protocolApplication.getProtocol())%>"><%=h(protocolApplication.getProtocol().getName())%></a>
            </td>
            <td valign="top"><%=formatDate(protocolApplication.getActivityDate())%></td>
            <td valign="top"><%=formatDate(protocolApplication.getStartTime())%></td>
            <td valign="top"><%=formatDate(protocolApplication.getEndTime())%></td>
            <td valign="top"><%=h(Objects.toString(protocolApplication.getRecordCount(), " "))%></td>
            <td valign="top">
                <% if (!props.isEmpty()) out.write(h(new JSONObject(props).toString(2))); %>
            </td>
        </tr>

        <% if (!materialRunInputs.isEmpty() || !dataRunInputs.isEmpty() || !materialRunOutputs.isEmpty() || !dataRunOutputs.isEmpty()) { %>
        <tr class="<%=text(rowCount%2==0 ? "labkey-row" : "labkey-alternate-row")%>">
            <td valign="top"></td>
            <td valign="top" colspan="8" style="padding: 5px;">
                <% if (!materialRunInputs.isEmpty() || !dataRunInputs.isEmpty()) { %>
                <b>Inputs</b><br>
                <table class="labkey-protocol-applications labkey-data-region-legacy labkey-show-borders" width="100%">
                    <% for (ExpMaterialRunInput materialRunInput : materialRunInputs) { %>
                    <% ExpMaterial material = materialRunInput.getMaterial(); %>
                    <tr class="labkey-row">
                        <td width="100px"><a href="<%=new ActionURL(ExperimentController.ShowMaterialAction.class, c).addParameter("rowId", material.getRowId())%>"><%= h(material.getName()) %></a></td>
                        <td width="40px"><%= h(materialRunInput.getRole()) %></td>
                        <td width="200px"><%= h(materialRunInput.getLSID()) %></td>
                        <td>
                            <% Map<String, Object> map = ((ExpMaterialRunInputImpl)materialRunInput).getProperties(); %>
                            <% if (!map.isEmpty()) out.write(h(new JSONObject(map).toString(2))); %>
                        </td>
                    </tr>
                    <% } %>

                    <% for (ExpDataRunInput dataRunInput : dataRunInputs) { %>
                    <% ExpData data = dataRunInput.getData(); %>
                    <tr class="labkey-row">
                        <td width="100px">
                            <a href="<%=new ActionURL(ExperimentController.ShowDataAction.class, c).addParameter("rowId", data.getRowId())%>"><%= h(data.getName()) %></a>
                            <% ExperimentDataHandler handler = data.findDataHandler();
                                ActionURL url = handler == null ? null : handler.getContentURL(data);
                                if (url != null) { %><%=textLink("view", url)%><% } %>
                        </td>
                        <td width="40px"><%= h(dataRunInput.getRole()) %></td>
                        <td width="200px"><%= h(dataRunInput.getLSID()) %></td>
                        <td>
                            <% Map<String, Object> map = ((ExpDataRunInputImpl)dataRunInput).getProperties(); %>
                            <% if (!map.isEmpty()) out.write(h(new JSONObject(map).toString(2))); %>
                        </td>
                    </tr>
                    <% } %>
                </table>
                <% } %>

                <% if (!materialRunOutputs.isEmpty() || !dataRunOutputs.isEmpty()) { %>
                <b>Outputs</b><br>
                <table class="labkey-protocol-applications labkey-data-region-legacy labkey-show-borders" width="100%">
                    <% for (ExpMaterialRunInput materialRunInput : materialRunOutputs) { %>
                    <% ExpMaterial material = materialRunInput.getMaterial(); %>
                    <tr class="labkey-row">
                        <td width="100px"><a href="<%=new ActionURL(ExperimentController.ShowMaterialAction.class, c).addParameter("rowId", material.getRowId())%>"><%= h(material.getName()) %></a></td>
                        <td width="40px"><%= h(materialRunInput.getRole()) %></td>
                        <td width="200px"><%= h(materialRunInput.getLSID()) %></td>
                        <td>
                            <% Map<String, Object> map = ((ExpMaterialRunInputImpl)materialRunInput).getProperties(); %>
                            <% if (!map.isEmpty()) out.write(h(new JSONObject(map).toString(2))); %>
                        </td>
                    </tr>
                    <% } %>

                    <% for (ExpDataRunInput dataRunInput : dataRunOutputs) { %>
                    <% ExpData data = dataRunInput.getData(); %>
                    <tr class="labkey-row">
                        <td width="100px">
                            <a href="<%=new ActionURL(ExperimentController.ShowDataAction.class, c).addParameter("rowId", data.getRowId())%>"><%= h(data.getName()) %></a>
                            <% ExperimentDataHandler handler = data.findDataHandler();
                                ActionURL url = handler == null ? null : handler.getContentURL(data);
                                if (url != null) { %><%=textLink("view", url)%><% } %>
                        </td>
                        <td width="40px"><%= h(dataRunInput.getRole()) %></td>
                        <td width="200px"><%= h(dataRunInput.getLSID()) %></td>
                        <td>
                            <% Map<String, Object> map = ((ExpDataRunInputImpl)dataRunInput).getProperties(); %>
                            <% if (!map.isEmpty()) out.write(h(new JSONObject(map).toString(2))); %>
                        </td>
                    </tr>
                    <% } %>
                </table>
                <% } %>
            </td>
        </tr>
        <% } // end if has inputs or outputs %>
    <% } %>
</table>

<% } else { // not debug %>

<table class="labkey-protocol-applications labkey-data-region-legacy labkey-show-borders">
    <tr>
        <td class="labkey-column-header">Name</td>
        <td class="labkey-column-header">Inputs</td>
        <td class="labkey-column-header">Outputs</td>
    </tr>
    <% for (ExpProtocolApplication protocolApplication : run.getProtocolApplications())
    {
        rowCount++;
    %>
        <tr class="<%=text(rowCount%2==0 ? "labkey-row" : "labkey-alternate-row")%>">
            <td valign="top">
                <a href="<%=h(ExperimentController.getShowApplicationURL(c, protocolApplication.getRowId()))%>"><%= h(protocolApplication.getName()) %></a>
            </td>
            <td valign="top">
                <% for (ExpMaterial material : protocolApplication.getInputMaterials()) { %>
                    <a href="<%=new ActionURL(ExperimentController.ShowMaterialAction.class, c).addParameter("rowId", material.getRowId())%>"><%= h(material.getName()) %></a><br>
                <% } %>
                <% for (ExpData data : protocolApplication.getInputDatas()) { %>
                    <a href="<%=new ActionURL(ExperimentController.ShowDataAction.class, c).addParameter("rowId", data.getRowId())%>"><%= h(data.getName()) %></a>
                <%
                    ExperimentDataHandler handler = data.findDataHandler();
                    ActionURL url = handler == null ? null : handler.getContentURL(data);
                    if (url != null) { %><%=textLink("view", url)%><% } %><br/>
                <% } %>
            </td>
            <td valign="top">
                <% for (ExpMaterial material : protocolApplication.getOutputMaterials()) { %>
                    <a href="<%=new ActionURL(ExperimentController.ShowMaterialAction.class, c).addParameter("rowId", material.getRowId())%>"><%= h(material.getName()) %></a><br>
                <% } %>
                <% for (ExpData data : protocolApplication.getOutputDatas()) { %>
                    <a href="<%=new ActionURL(ExperimentController.ShowDataAction.class, c).addParameter("rowId", data.getRowId())%>"><%= h(data.getName()) %></a>
                <%
                    ExperimentDataHandler handler = data.findDataHandler();
                    ActionURL url = handler == null ? null : handler.getContentURL(data);
                    if (url != null) { %><%=textLink("view", url)%><% } %><br/>
                <% } %>
            </td>
        </tr>
    <% } %>
</table>

<% } %>

