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
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.labkey.api.study.actions.AssayRunUploadForm" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<AssayRunUploadForm> me = (JspView<AssayRunUploadForm>) HttpView.currentView();
    AssayRunUploadForm bean = me.getModelBean();
%>
<labkey:form layout="horizontal">
    <labkey:input type="displayfield" label="Name" value="<%= text(bean.getProtocol().getName()) %>" />
    <% if (!StringUtils.isEmpty(bean.getProtocol().getDescription())) { %>
    <labkey:input type="displayfield" label="Description" value="<%= text(bean.getProtocol().getProtocolDescription()) %>" />
    <% } %>
</labkey:form>