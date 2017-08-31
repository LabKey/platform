<%
/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.assay.dilution.DilutionAssayRun" %>
<%@ page import="org.labkey.api.assay.nab.RenderAssayBean" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("announcements/discuss.js");
        dependencies.add("nabqc");
    }
%>
<br/>
<labkey:errors/>
<%
    JspView<RenderAssayBean> me = (JspView<RenderAssayBean>) HttpView.currentView();
    RenderAssayBean bean = me.getModelBean();
    DilutionAssayRun assay = bean.getAssay();

    boolean writer = getContainer().hasPermission(getUser(), InsertPermission.class);
%>
<%
    if (bean.needsNotesView())
    {
%>
    <div class="panel panel-default">
        <div class="panel-heading clearfix">
            <h3 class="panel-title pull-left">
                Notes
            </h3>
        </div>
        <div class="panel-body">
            <% me.include(bean.getRunNotesView(), out); %>
        </div>
    </div>
<%
    }
%>
    <div class="panel panel-default">
        <div class="panel-heading clearfix">
            <h3 class="panel-title pull-left">
                Run Summary<%= h(assay.getRunName() != null ? ": " + assay.getRunName() : "") %>
            </h3>
        </div>
        <div class="panel-body">
            <% me.include(bean.getRunPropertiesView(), out); %>
            <br/>
            <table>
                <tr>
                    <td valign="top"><% me.include(bean.getGraphView(), out); %></td>
                    <td valign="top"><% include(bean.getControlsView(), out); %><br>
                        <% me.include(bean.getCutoffsView(), out); %>
                    </td>
                </tr>
            </table>
            <br/>
            <% me.include(bean.getSamplePropertiesView(), out); %>
        </div>
    </div>

    <div class="panel panel-default">
        <div class="panel-heading clearfix">
            <h3 class="panel-title pull-left">
                <%=h(bean.getSampleNoun())%> Information
            </h3>
        </div>
        <div class="panel-body">
            <% me.include(bean.getSampleDilutionsView(), out); %>
        </div>
    </div>

    <div class="panel panel-default">
        <div class="panel-heading clearfix">
            <h3 class="panel-title pull-left">
                Plate Data
            </h3>
        </div>
        <div class="panel-body">
            <% me.include(bean.getPlateDataView(), out); %>
        </div>
    </div>

<%
    if (!bean.isPrintView() && writer)
    {
%>
    <div class="panel panel-default">
        <div class="panel-heading clearfix">
            <h3 class="panel-title pull-left">
                Discussions
            </h3>
        </div>
        <div class="panel-body">
            <% me.include(bean.getDiscussionView(getViewContext()), out); %>
        </div>
    </div>
<%
    }
%>