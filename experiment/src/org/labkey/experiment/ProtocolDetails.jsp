<%
/*
 * Copyright (c) 2005-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>

<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ExpProtocol> me = (JspView<ExpProtocol>) HttpView.currentView();
    ExpProtocol protocol = me.getModelBean();
%>

<table class="lk-fields-table">
    <tr>
        <td class="labkey-form-label">Name</td>
        <td><%= h(protocol.getName()) %></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Contact</td>
        <td><%= h(protocol.getContact()) %></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Instrument</td>
        <td><%= h(protocol.getInstrument()) %></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Software</td>
        <td><%= h(protocol.getSoftware()) %></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Description</td>
        <td><%= h(protocol.getProtocolDescription()) %></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Max input data per instance</td>
        <td><%= h(protocol.getMaxInputDataPerInstance()) %></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Max input material per instance</td>
        <td><%= h(protocol.getMaxInputMaterialPerInstance()) %></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Output data per instance</td>
        <td><%= h(protocol.getOutputDataPerInstance()) %></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Output material per instance</td>
        <td><%= h(protocol.getOutputMaterialPerInstance()) %></td>
    </tr>
</table>