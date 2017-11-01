<%
/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
<%@ page import="org.labkey.study.controllers.security.SecurityController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>

<labkey:form action="<%=h(buildURL(SecurityController.ExportSecurityPolicyAction.class))%>">
    Export Policy As XML File: <br/>
    <%= button("Export").submit(true) %>
</labkey:form>
<br/><br/>
<labkey:form action="<%=h(buildURL(SecurityController.ImportSecurityPolicyAction.class))%>" enctype="multipart/form-data" method="post">
    Import Policy From XML File: <br/>
    <input type="file" name="fileUpload" style="border: none; padding-bottom: 5px;"/>
    <%= button("Import").submit(true) %>
</labkey:form>
