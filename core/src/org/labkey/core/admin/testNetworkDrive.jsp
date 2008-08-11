<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.admin.TestNetworkDriveBean" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<TestNetworkDriveBean> me = (JspView<TestNetworkDriveBean>) HttpView.currentView();
    TestNetworkDriveBean bean = me.getModelBean();
%>
<labkey:errors />

<% if (bean.getFiles() != null) {  %>
    <p><b>Success!</b></p>
    Drive contents:<br/>
    <ul>
        <% for (String file : bean.getFiles()) { %>
            <li><%= file %></li>
        <% } %>
    </ul>
<% } %>
