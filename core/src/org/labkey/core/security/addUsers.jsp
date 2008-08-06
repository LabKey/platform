<%
/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.user.UserController" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<script type="text/javascript">LABKEY.requiresScript('completion.js');</script>
<script type="text/javascript">
    var permissionLink_hide = "<a href=\"#blank\" style=\"display:none\" onclick=\"showUserAccess();\">[permissions]</a>";
    var permissionLink_show = "<a href=\"#blank\" onclick=\"showUserAccess();\">[permissions]</a>";

    function enableText()
    {
        var checkBoxElem = document.getElementById("cloneUserCheck");
        var textElem = document.getElementById("cloneUser");

        if (checkBoxElem != null && textElem != null)
        {
            var checked = checkBoxElem.checked;

            textElem.disabled = !checked;
            textElem.value = "";

            var permissionElem = document.getElementById("permissions");
            if (permissionElem != null)
                permissionElem.innerHTML = checked ? permissionLink_show : permissionLink_hide;
        }
    }

    function showUserAccess()
    {
        var textElem = document.getElementById("cloneUser");
        if (textElem != null)
        {
            if (textElem.value != null && textElem.value.length > 0)
            {
                var target = "<%= new ActionURL(UserController.UserAccessAction.class, ContainerManager.getRoot())%>newEmail=" + textElem.value;
                window.open(target, "permissions", "height=450,width=500,scrollbars=yes,status=yes,toolbar=no,menubar=no,location=no,resizable=yes");
            }
        }
    }
</script>

<form action="addUsers.post" method=post>
    <labkey:errors />

    <table>
        <tr>
            <td valign="top">Add new users.<br><br>Enter one or more email addresses,&nbsp;<br>each on its own line.</td>
            <td colspan="2">
                <textarea name="newUsers" id="newUsers" cols=40 rows=20></textarea>
            </td>
        <tr>
            <td><input type=checkbox id="cloneUserCheck" name="cloneUserCheck" onclick="enableText();">Clone permissions from user:</td>
            <td>
                <input type=text name="cloneUser" id="cloneUser" disabled="true"
                        onKeyDown="return ctrlKeyCheck(event);"
                        onBlur="hideCompletionDiv();"
                        autocomplete="off"
                        onKeyUp="return handleChange(this, event, 'completeUser.view?prefix=');">
            </td>
            <td><div id=permissions><a href="#blank" style="display:none" onclick="showUserAccess();">[permissions]</a></div></td>
        </tr>
        <tr><td colspan="3">
            <input type=checkbox name="sendMail" id="sendMail" checked><label for="sendmail">Send notification emails to all new<%
            String LDAPDomain = AuthenticationManager.getLdapDomain();
            if (LDAPDomain != null && LDAPDomain.length() > 0)
            {
                %>, non-<%=LDAPDomain%><%
            }
            %> users</label><br><br>
        </td></tr>
        <tr>
            <td>
                <labkey:button text="Add Users" />
                <%=PageFlowUtil.buttonLink("Show Grid", request.getContextPath() + "/User/showUsers.view?.lastFilter=true")%>
            </td>
        </tr>
    </table>
</form>
<script for=window event=onload type="text/javascript">try {document.getElementById("newUsers").focus();} catch(x){}</script>
