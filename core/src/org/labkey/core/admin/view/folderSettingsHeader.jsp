<%
/*
 * Copyright (c) 2011 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<labkey:errors/>

<table>
    <tr><td class="labkey-announcement-title"><span>Email Notification Settings</span></td></tr>
    <tr><td class="labkey-title-area-line"></td></tr>
    <tr><td>The list of users below contains all users that have READ access to this folder and are available to receive notifications
        by email for message boards and file content events.<br/>
        A users current message or file notification setting will be visible in the appropriately named column. Administrators
        may bulk edit users settings by clicking on the 'Admin' button to expose the configuration panels for each
        notification type. Configuration affects only the users that are selected with the record check boxes.
    </td></tr>
</table>
