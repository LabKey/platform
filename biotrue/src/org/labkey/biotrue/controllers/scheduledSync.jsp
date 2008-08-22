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
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.biotrue.controllers.BtController" %>
<%@ page import="org.labkey.biotrue.datamodel.Server" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    BtController.ServerUpdateForm form = (BtController.ServerUpdateForm) __form;
    Server server = form.getServer()._server;
%>
<labkey:errors />


<form action="scheduledSync.view" method="post">
    <table>
        <input type="hidden" name="serverId" value="<%=server.getRowId()%>">
        <tr><td align=center><div class="labkey-form-label"><b>Scheduled Synchronization</b></div></td></tr>
        <tr><td>Server:&nbsp;<%=server.getName()%></td></tr>
        <tr><td>Synchronize with the BioTrue server:&nbsp;
            <select name="serverSyncInterval">
                <option value="0" <%=getSelected("0", server.getSyncInterval())%>>manually</option>
                <option value="1" <%=getSelected("1", server.getSyncInterval())%>>every 1 hour</option>
                <option value="2" <%=getSelected("2", server.getSyncInterval())%>>every 2 hours</option>
                <option value="4" <%=getSelected("4", server.getSyncInterval())%>>every 4 hours</option>
                <option value="6" <%=getSelected("6", server.getSyncInterval())%>>every 6 hours</option>
                <option value="12" <%=getSelected("12", server.getSyncInterval())%>>every 12 hours</option>
                <option value="24" <%=getSelected("24", server.getSyncInterval())%>>every 24 hours</option>
            </select>
        <tr><td></td></tr>
        <tr><td>
            <%=PageFlowUtil.generateButton("Cancel", HttpView.currentContext().cloneActionURL().setAction(BtController.AdminAction.class))%>&nbsp;
            <%=PageFlowUtil.generateSubmitButton("Update")%>&nbsp;
        </td></tr>
        <tr><td>&nbsp;</td></tr>
    </table>
</form>

<%!
    public String getSelected(String value, int option) {
        if (StringUtils.equals(value, String.valueOf(option)))
            return "selected";
        return "";
    }
%>

