<%--
/*
 * Copyright (c) 2017 LabKey Corporation
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
--%>
<%@ page buffer="none" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.core.view.template.bootstrap.PageTemplate" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<PageConfig> me = (JspView<PageConfig>) HttpView.currentView();
    PageConfig pageConfig = me.getModelBean();
%>
<div class="container">
    <div class="row content-row">
    <div class="content-left">
    <%= text(PageTemplate.renderSiteMessages(pageConfig)) %>
    <div class="labkey-wizard-container">
        <div class="well">
            <span class="labkey-nav-page-header"><%= h(pageConfig.getTitle()) %></span>
            <div class="row labkey-wizard-row hidden-md hidden-lg">
                <div class="col-xs-12">
                    <ul class="nav nav-pills list-inline">
                        <%
                            int index = 1;
                            for (NavTree navTree : pageConfig.getNavTrail())
                            { %>
                        <li <%= (navTree.getText().equals(pageConfig.getTitle()) || (navTree.getText().equals("Create Folder") && pageConfig.getTitle().startsWith("Create Folder"))) ? "class=\"active\"" : ""%>>
                            <a><span class="list-group-item-heading">Step <%=(index++)%></span></a>
                        </li>
                        <%
                            }%>
                    </ul>
                </div>
            </div>
            <div class="row labkey-wizard-row">
                <div class="col-md-3 hidden-xs hidden-sm">
                    <ul class="nav nav-stacked labkey-wizard-pills">
                        <% for (NavTree navTree : pageConfig.getNavTrail())
                        { %>
                            <li <%= (navTree.getText().equals(pageConfig.getTitle()) || (navTree.getText().equals("Create Folder") && pageConfig.getTitle().startsWith("Create Folder"))) ? "class=\"active\"" : ""%>>
                                <a><%= navTree.getText() %></a>
                            </li>
                        <% } %>
                    </ul>
                </div>
                <div class="col-md-9 labkey-wizard-content">
                    <table class="tab-content">
                        <tr>
                            <td class="labkey-wizard-divider hidden-xs hidden-sm"></td>
                            <td class="labkey-wizard-content-body"><% me.include(me.getBody(), out);%></td>
                        </tr>
                    </table>
                </div>
            </div>
        </div>
    </div>
    </div>
    </div>
</div>