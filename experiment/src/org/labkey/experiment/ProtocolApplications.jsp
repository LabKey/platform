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
<%@ page import="org.json.old.JSONObject"%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.exp.ExperimentDataHandler" %>
<%@ page import="org.labkey.api.exp.Identifiable" %>
<%@ page import="org.labkey.api.exp.LsidManager" %>
<%@ page import="org.labkey.api.exp.api.ExpData" %>
<%@ page import="org.labkey.api.exp.api.ExpDataProtocolInput" %>
<%@ page import="org.labkey.api.exp.api.ExpDataRunInput" %>
<%@ page import="org.labkey.api.exp.api.ExpMaterial" %>
<%@ page import="org.labkey.api.exp.api.ExpMaterialProtocolInput" %>
<%@ page import="org.labkey.api.exp.api.ExpMaterialRunInput" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocolApplication" %>
<%@ page import="org.labkey.api.exp.api.ExpRun" %>
<%@ page import="org.labkey.api.exp.api.ProvenanceService" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.api.ExpDataRunInputImpl" %>
<%@ page import="org.labkey.experiment.api.ExpMaterialRunInputImpl" %>
<%@ page import="org.labkey.experiment.api.ExpProtocolApplicationImpl" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Objects" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ExpRun> me = (JspView<ExpRun>) HttpView.currentView();
    ExpRun run = me.getModelBean();
    Container c = getContainer();
    int rowCount = 0;

    boolean debug = getViewContext().getRequest().getParameter("_debug") != null;
    ProvenanceService pvs = ProvenanceService.get();

%>
<%!
    final Pair<Identifiable, ActionURL> EMPTY_PAIR = Pair.of(null, null);
    Map<String, Pair<Identifiable, ActionURL>> provCache = new HashMap<>();
    Pair<Identifiable, ActionURL> getObject(String lsid)
    {
        if (lsid == null)
            return EMPTY_PAIR;

        return provCache.computeIfAbsent(lsid, (l) -> {
            Identifiable obj = LsidManager.get().getObject(lsid);
            if (obj == null)
                return EMPTY_PAIR;
            ActionURL url = LsidManager.get().getDisplayURL(lsid);
            return Pair.of(obj, url);
        });
    }
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

        Set<Pair<String, String>> provenance = Collections.emptySet();
        provenance = pvs.getProvenanceObjectUris(protocolApplication.getRowId());
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
                <a href="<%=h(ExperimentController.ExperimentUrlsImpl.get().getProtocolDetailsURL(protocolApplication.getProtocol()))%>"><%=h(protocolApplication.getProtocol().getName())%></a>
            </td>
            <td valign="top"><%=formatDate(protocolApplication.getActivityDate())%></td>
            <td valign="top"><%=formatDate(protocolApplication.getStartTime())%></td>
            <td valign="top"><%=formatDate(protocolApplication.getEndTime())%></td>
            <td valign="top"><%=h(Objects.toString(protocolApplication.getRecordCount(), " "))%></td>
            <td valign="top">
                <%
                    if (!props.isEmpty())
                        out.print(new JSONObject(props).getJavaScriptFragment(2));
                %>
            </td>
        </tr>

        <% if (!materialRunInputs.isEmpty() || !dataRunInputs.isEmpty() || !materialRunOutputs.isEmpty() || !dataRunOutputs.isEmpty() || !provenance.isEmpty()) { %>
        <tr class="<%=text(rowCount%2==0 ? "labkey-row" : "labkey-alternate-row")%>">
            <td valign="top"></td>
            <td valign="top" colspan="8" style="padding: 5px;">
                <% if (!materialRunInputs.isEmpty() || !dataRunInputs.isEmpty()) { %>
                <b>Inputs</b><br>
                <table class="labkey-protocol-applications labkey-data-region-legacy labkey-show-borders" width="100%">
                    <% for (ExpMaterialRunInput materialRunInput : materialRunInputs) { %>
                    <% ExpMaterial material = materialRunInput.getMaterial(); %>
                    <% ExpMaterialProtocolInput protocolInput = materialRunInput.getProtocolInput(); %>
                    <tr class="labkey-row">
                        <td width="100px"><a href="<%=h(new ActionURL(ExperimentController.ShowMaterialAction.class, c).addParameter("rowId", material.getRowId()))%>"><%= h(material.getName()) %></a></td>
                        <td width="40px"><%= h(materialRunInput.getRole()) %></td>
                        <td width="200px"><%= h(materialRunInput.getLSID()) %></td>
                        <td width="200px"><%= h(protocolInput != null ? protocolInput.getName() : null)%></td>
                        <td>
                            <% Map<String, Object> map = ((ExpMaterialRunInputImpl)materialRunInput).getProperties(); %>
                            <% if (!map.isEmpty()) out.print(new JSONObject(map).getJavaScriptFragment(2)); %>
                        </td>
                    </tr>
                    <% } %>

                    <% for (ExpDataRunInput dataRunInput : dataRunInputs) { %>
                    <% ExpData data = dataRunInput.getData(); %>
                    <% ExpDataProtocolInput protocolInput = dataRunInput.getProtocolInput(); %>
                    <tr class="labkey-row">
                        <td width="100px">
                            <a href="<%=h(new ActionURL(ExperimentController.ShowDataAction.class, c).addParameter("rowId", data.getRowId()))%>"><%= h(data.getName()) %></a>
                            <% ExperimentDataHandler handler = data.findDataHandler();
                                ActionURL url = handler == null ? null : handler.getContentURL(data);
                                if (url != null) { %><%=link("view", url)%><% } %>
                        </td>
                        <td width="40px"><%= h(dataRunInput.getRole()) %></td>
                        <td width="200px"><%= h(dataRunInput.getLSID()) %></td>
                        <td width="200px"><%= h(protocolInput != null ? protocolInput.getName() : null)%></td>
                        <td>
                            <% Map<String, Object> map = ((ExpDataRunInputImpl)dataRunInput).getProperties(); %>
                            <% if (!map.isEmpty()) out.print(new JSONObject(map).getJavaScriptFragment(2)); %>
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
                    <% ExpMaterialProtocolInput protocolInput = materialRunInput.getProtocolInput(); %>
                    <tr class="labkey-row">
                        <td width="100px"><a href="<%=h(new ActionURL(ExperimentController.ShowMaterialAction.class, c).addParameter("rowId", material.getRowId()))%>"><%= h(material.getName()) %></a></td>
                        <td width="40px"><%= h(materialRunInput.getRole()) %></td>
                        <td width="200px"><%= h(materialRunInput.getLSID()) %></td>
                        <td width="200px"><%= h(protocolInput != null ? protocolInput.getName() : null)%></td>
                        <td>
                            <% Map<String, Object> map = ((ExpMaterialRunInputImpl)materialRunInput).getProperties(); %>
                            <% if (!map.isEmpty()) out.print(new JSONObject(map).getJavaScriptFragment(2)); %>
                        </td>
                    </tr>
                    <% } %>

                    <% for (ExpDataRunInput dataRunInput : dataRunOutputs) { %>
                    <% ExpData data = dataRunInput.getData(); %>
                    <% ExpDataProtocolInput protocolInput = dataRunInput.getProtocolInput(); %>
                    <tr class="labkey-row">
                        <td width="100px">
                            <a href="<%=h(new ActionURL(ExperimentController.ShowDataAction.class, c).addParameter("rowId", data.getRowId()))%>"><%= h(data.getName()) %></a>
                            <% ExperimentDataHandler handler = data.findDataHandler();
                                ActionURL url = handler == null ? null : handler.getContentURL(data);
                                if (url != null) { %><%=link("view", url)%><% } %>
                        </td>
                        <td width="40px"><%= h(dataRunInput.getRole()) %></td>
                        <td width="200px"><%= h(dataRunInput.getLSID()) %></td>
                        <td width="200px"><%= h(protocolInput != null ? protocolInput.getName() : null)%></td>
                        <td>
                            <% Map<String, Object> map = ((ExpDataRunInputImpl)dataRunInput).getProperties(); %>
                            <% if (!map.isEmpty()) out.print(new JSONObject(map).getJavaScriptFragment(2)); %>
                        </td>
                    </tr>
                    <% } %>
                </table>
                <% } %>

                <% if (!provenance.isEmpty()) { %>
                <b>Provenance</b><br>
                <table class="labkey-protocol-applications labkey-data-region-legacy labkey-show-borders" width="100%">
                    <% for (Pair<String, String> pair : provenance) { %>
                    <% String fromLsid = pair.first; %>
                    <% String toLsid = pair.second; %>
                    <% Pair<Identifiable, ActionURL> fromObj = getObject(fromLsid); %>
                    <% Pair<Identifiable, ActionURL> toObj = getObject(toLsid); %>
                    <tr class="labkey-row">
                        <td width="100px">
                            <% if (fromObj == EMPTY_PAIR || fromObj.second == null) { %>
                            <%= h(fromLsid) %>
                            <% } else { %>
                            <a href="<%=h(fromObj.second)%>" title="<%=h(fromLsid)%>"><%= h(fromObj.first.getName()) %></a>
                            <% } %>
                        </td>
                        <td width="100px">
                            <% if (toObj == EMPTY_PAIR || toObj.second == null) { %>
                            <%= h(toLsid) %>
                            <% } else { %>
                            <a href="<%=h(toObj.second)%>" title="<%=h(toLsid)%>"><%= h(toObj.first.getName()) %></a>
                            <% } %>
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
                    <a href="<%=h(new ActionURL(ExperimentController.ShowMaterialAction.class, c).addParameter("rowId", material.getRowId()))%>"><%= h(material.getName()) %></a><br>
                <% } %>
                <% for (ExpData data : protocolApplication.getInputDatas()) { %>
                    <a href="<%=h(new ActionURL(ExperimentController.ShowDataAction.class, c).addParameter("rowId", data.getRowId()))%>"><%= h(data.getName()) %></a>
                <%
                    ExperimentDataHandler handler = data.findDataHandler();
                    ActionURL url = handler == null ? null : handler.getContentURL(data);
                    if (url != null) { %><%=link("view", url)%><% } %><br/>
                <% } %>
            </td>
            <td valign="top">
                <% for (ExpMaterial material : protocolApplication.getOutputMaterials()) { %>
                    <a href="<%=h(new ActionURL(ExperimentController.ShowMaterialAction.class, c).addParameter("rowId", material.getRowId()))%>"><%= h(material.getName()) %></a><br>
                <% } %>
                <% for (ExpData data : protocolApplication.getOutputDatas()) { %>
                    <a href="<%=h(new ActionURL(ExperimentController.ShowDataAction.class, c).addParameter("rowId", data.getRowId()))%>"><%= h(data.getName()) %></a>
                <%
                    ExperimentDataHandler handler = data.findDataHandler();
                    ActionURL url = handler == null ? null : handler.getContentURL(data);
                    if (url != null) { %><%=link("view", url)%><% } %><br/>
                <% } %>
            </td>
        </tr>
    <% } %>
</table>

<% } %>

