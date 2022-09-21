<%
/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.ProtocolParameter" %>
<%@ page import="org.labkey.api.exp.api.ExpDataClass" %>
<%@ page import="org.labkey.api.exp.api.ExpDataProtocolInput" %>
<%@ page import="org.labkey.api.exp.api.ExpMaterialProtocolInput" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocolInputCriteria" %>
<%@ page import="org.labkey.api.exp.api.ExpSampleType" %>
<%@ page import="org.labkey.api.exp.api.ExperimentUrls" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.api.ExpDataProtocolInputImpl" %>
<%@ page import="org.labkey.experiment.api.ExpMaterialProtocolInputImpl" %>
<%@ page import="org.labkey.experiment.api.ExpProtocolActionImpl" %>
<%@ page import="org.labkey.experiment.api.ExpProtocolImpl" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController.ProtocolPredecessorsAction" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.stream.Collectors" %>

<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ExpProtocolImpl> me = (JspView<ExpProtocolImpl>) HttpView.currentView();
    final ExpProtocolImpl protocol = me.getModelBean();
    final ActionURL ppURL = urlFor(ProtocolPredecessorsAction.class);
    ppURL.addParameter("ParentLSID", protocol.getLSID());

    ExperimentUrls urls = urlProvider(ExperimentUrls.class);
    assert urls != null;

    List<ExpProtocolActionImpl> steps = protocol.getSteps();
    boolean debug = getViewContext().getRequest().getParameter("_debug") != null;
    int rowCount = 0;
%>

<table class="labkey-data-region-legacy labkey-show-borders">
    <tr>
        <td class="labkey-column-header">Step</td>
        <td class="labkey-column-header">Name</td>
        <td class="labkey-column-header">Description</td>
        <% if (debug) { %>
        <td class="labkey-column-header">Type</td>
        <td class="labkey-column-header">Predecessors</td>
        <td class="labkey-column-header">Properties</td>
        <% }%>
    </tr>
<% for (ExpProtocolActionImpl step : steps)
{
    rowCount++;
    int actionSequence = step.getActionSequence();
    ExpProtocolImpl childProtocol = step.getChildProtocol();
    String name = childProtocol.getName();
    String description = childProtocol.getDescription();

    String predecessorNames = null;
    Map<String, Object> props = null;
    Map<String, ProtocolParameter> params = null;
    List<? extends ExpMaterialProtocolInput> materialProtocolInputs = null;
    List<? extends ExpMaterialProtocolInput> materialProtocolOutputs = null;
    List<? extends ExpDataProtocolInput> dataProtocolInputs = null;
    List<? extends ExpDataProtocolInput> dataProtocolOutputs = null;
    List<? extends ExpProtocol> parentProtocols = null;
    boolean hasExtraInfo = false;
    if (debug)
    {
        List<ExpProtocolActionImpl> predecessors = step.getPredecessors();
        predecessorNames = predecessors.stream().map(p -> p.getChildProtocol().getName()).collect(Collectors.joining(", "));

        props = childProtocol.getProperties();

        params = childProtocol.getProtocolParameters();

        materialProtocolInputs = childProtocol.getMaterialProtocolInputs();
        materialProtocolOutputs = childProtocol.getMaterialProtocolOutputs();
        dataProtocolInputs = childProtocol.getDataProtocolInputs();
        dataProtocolOutputs = childProtocol.getDataProtocolOutputs();

        // Other usages of the protocol
        parentProtocols = new ArrayList<>(childProtocol.getParentProtocols());
        parentProtocols.remove(protocol);

        hasExtraInfo = !params.isEmpty() ||
                !parentProtocols.isEmpty() ||
                !materialProtocolInputs.isEmpty() || !dataProtocolInputs.isEmpty() ||
                !materialProtocolOutputs.isEmpty() || !dataProtocolOutputs.isEmpty();
    }
%>
    <tr class="<%=text(rowCount%2==0 ? "labkey-row" : "labkey-alternate-row")%>">
        <td valign="top">
            <%=actionSequence%>
        </td>
        <td valign="top">
            <a href="<%=h(ppURL.clone().addParameter("Sequence", actionSequence))%>"><%=h(name)%></a>
        </td>
        <td valign="top">
            <%=h(description)%>
        </td>
        <% if (debug) { %>
        <td valign="top">
            <%=h(childProtocol.getApplicationType().name())%>
        </td>
        <td valign="top">
            <%=h(predecessorNames)%>
        </td>
        <td valign="top">
            <% if (!props.isEmpty()) out.print(new JSONObject(props).getJavaScriptFragment(2)); %>
        </td>
        <% } %>
    </tr>

        <% if (debug && hasExtraInfo) { %>
    <tr class="<%=text(rowCount%2==0 ? "labkey-row" : "labkey-alternate-row")%>">
        <td valign="top"></td>
        <td valign="top" colspan="8" style="padding: 5px;">
            <% if (!parentProtocols.isEmpty()) { %>
            <b>Also Used In:</b>
                <% for (ExpProtocol parentProtocol : parentProtocols) { %>
            <a href="<%=h(urls.getProtocolDetailsURL(parentProtocol))%>"><%=h(parentProtocol.getName())%></a>
                <% } %>
            <br>
            <% } %>

            <% if (!params.isEmpty()) { %>
            <b>Parameters</b><br>
            <table class="labkey-data-region-legacy labkey-show-borders" width="100%">
                <% for (ProtocolParameter param : params.values()) { %>
                <tr class="labkey-row">
                    <td width="80px"><%=h(param.getName())%></td>
                    <td width="200px"><%=h(param.getOntologyEntryURI())%></td>
                    <td width="60px"><%=h(param.getValueType())%></td>
                    <td width="500px"><%=h(param.getValue())%></td>
                </tr>
                <% } %>
            </table>
            <% } %>

            <% if (!materialProtocolInputs.isEmpty() || !dataProtocolInputs.isEmpty()) { %>
            <b>Inputs</b><br>
            <table class="labkey-data-region-legacy labkey-show-borders" width="100%">
                <% for (ExpMaterialProtocolInput mpi : materialProtocolInputs) { %>
                <% ExpSampleType st = mpi.getType(); %>
                <% ExpProtocolInputCriteria criteria = mpi.getCriteria(); %>
                <tr class="labkey-row">
                    <td width="100px"><%=h(mpi.getName())%></td>
                    <td width="80px">
                        <% if (st != null) { %>
                        <a href="<%=h(st.detailsURL())%>"><%=h(st.getName())%></a>
                        <% } %>
                    </td>
                    <td width="200px"><%=h(mpi.getLSID())%></td>
                    <td width="80px"><%=h(criteria != null ? criteria.getTypeName() : null)%></td>
                    <td width="20px"><%=mpi.getMinOccurs()%></td>
                    <td width="20px"><%=mpi.getMaxOccurs()%></td>
                    <td>
                        <% Map<String, Object> map = ((ExpMaterialProtocolInputImpl)mpi).getProperties(); %>
                        <% if (!map.isEmpty()) out.print(new JSONObject(map).getJavaScriptFragment(2)); %>
                    </td>
                </tr>
                <% } %>

                <% for (ExpDataProtocolInput dpi : dataProtocolInputs) { %>
                <% ExpDataClass dc = dpi.getType(); %>
                <% ExpProtocolInputCriteria criteria = dpi.getCriteria(); %>
                <tr class="labkey-row">
                    <td width="100px"><%=h(dpi.getName())%></td>
                    <td width="80px">
                        <% if (dc != null) { %>
                        <a href="<%=h(dc.detailsURL())%>"><%=h(dc.getName())%></a>
                        <% } %>
                    </td>
                    <td width="200px"><%=h(dpi.getLSID())%></td>
                    <td width="80px"><%=h(criteria != null ? criteria.getTypeName() : null)%></td>
                    <td width="20px"><%=dpi.getMinOccurs()%></td>
                    <td width="20px"><%=dpi.getMaxOccurs()%></td>
                    <td>
                        <% Map<String, Object> map = ((ExpDataProtocolInputImpl)dpi).getProperties(); %>
                        <% if (!map.isEmpty()) out.print(new JSONObject(map).getJavaScriptFragment(2)); %>
                    </td>
                </tr>
                <% } %>
            </table>
            <% } %>

            <% if (!materialProtocolOutputs.isEmpty() || !dataProtocolOutputs.isEmpty()) { %>
            <b>Outputs</b><br>
            <table class="labkey-data-region-legacy labkey-show-borders" width="100%">
                <% for (ExpMaterialProtocolInput mpo : materialProtocolOutputs) { %>
                <% ExpProtocolInputCriteria criteria = mpo.getCriteria(); %>
                <% ExpSampleType st = mpo.getType(); %>
                <tr class="labkey-row">
                    <td width="100px"><%=h(mpo.getName())%></td>
                    <td width="80px">
                        <% if (st != null) { %>
                        <a href="<%=h(st.detailsURL())%>"><%=h(st.getName())%></a>
                        <% } %>
                    </td>
                    <td width="200px"><%=h(mpo.getLSID())%></td>
                    <td width="80px"><%=h(criteria != null ? criteria.getTypeName() : null)%></td>
                    <td width="20px"><%=mpo.getMinOccurs()%></td>
                    <td width="20px"><%=h(mpo.getMaxOccurs())%></td>
                    <td>
                        <% Map<String, Object> map = ((ExpMaterialProtocolInputImpl)mpo).getProperties(); %>
                        <% if (!map.isEmpty()) out.print(new JSONObject(map).getJavaScriptFragment(2)); %>
                    </td>
                </tr>
                <% } %>

                <% for (ExpDataProtocolInput dpo : dataProtocolOutputs) { %>
                <% ExpDataClass dc = dpo.getType(); %>
                <% ExpProtocolInputCriteria criteria = dpo.getCriteria(); %>
                <tr class="labkey-row">
                    <td width="100px"><%=h(dpo.getName())%></td>
                    <td width="80px">
                        <% if (dc != null) { %>
                        <a href="<%=h(dc.detailsURL())%>"><%=h(dc.getName())%></a>
                        <% } %>
                    </td>
                    <td width="200px"><%=h(dpo.getLSID())%></td>
                    <td width="80px"><%=h(criteria != null ? criteria.getTypeName() : null)%></td>
                    <td width="20px"><%=dpo.getMinOccurs()%></td>
                    <td width="20px"><%=dpo.getMaxOccurs()%></td>
                    <td>
                        <% Map<String, Object> map = ((ExpDataProtocolInputImpl)dpo).getProperties(); %>
                        <% if (!map.isEmpty()) out.print(new JSONObject(map).getJavaScriptFragment(2)); %>
                    </td>
                </tr>
                <% } %>
            </table>
            <% } %>

        </td>
    </tr>
        <% } %>
<%
}
%>
</table>
