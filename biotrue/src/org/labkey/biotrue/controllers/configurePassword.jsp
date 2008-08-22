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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.biotrue.controllers.ServerForm" %>
<%@ page import="org.labkey.biotrue.datamodel.Server" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    ServerForm form = (ServerForm) __form;
    Server server = form.getServer()._server;
%>
<labkey:errors />


<form action="" method="post">
    <table>
        <input type="hidden" name="serverId" value="<%=server.getRowId()%>">
        <tr><td align=center><div class="labkey-form-label"><b>Set Server Password</b></div></td></tr>
        <tr><td>Server:&nbsp;<%=server.getName()%></td><td><input name="password" value="<%=StringUtils.trimToEmpty(server.getPassword())%>"></td></tr>
        <tr><td colspan="2">
            <%=PageFlowUtil.generateButton("Cancel", form.getContext().cloneActionURL().setAction("admin.view"))%>&nbsp;
            <%=PageFlowUtil.generateSubmitButton("Update")%>&nbsp;
        </td></tr>
        <tr><td>&nbsp;</td></tr>
    </table>
</form>

