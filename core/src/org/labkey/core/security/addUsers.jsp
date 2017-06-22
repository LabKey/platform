<%
/*
 * Copyright (c) 2005-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page import="org.labkey.core.security.SecurityController.AddUsersForm" %>
<%@ page import="org.labkey.core.user.UserController" %>
<%@ page import="org.labkey.core.user.UserController.UserUrlsImpl" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("completion");
    }
%>
<%
    AddUsersForm form = (AddUsersForm)HttpView.currentModel();
%>
<script type="text/javascript">
    document.addEventListener("DOMContentLoaded", function() {
        if (LABKEY.ActionURL.getParameter('provider')) {
            document.getElementById('provider').value = LABKEY.ActionURL.getParameter('provider');
        }
    });
    var permissionLink_hide = '<a href="#blank" style="display:none" onclick="showUserAccess();">permissions<\/a>';
    var permissionLink_show = '<a href="#blank" class="labkey-button" onclick="showUserAccess();"><span>permissions</span><\/a>';

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
                var target = "<%= new UserUrlsImpl().getUserAccessURL(ContainerManager.getRoot())%>newEmail=" + textElem.value;
                window.open(target, "permissions", "height=450,width=500,scrollbars=yes,status=yes,toolbar=no,menubar=no,location=no,resizable=yes");
            }
        }
    }

    Ext4.onReady(function(){

        Ext4.create('LABKEY.element.AutoCompletionField', {
            renderTo        : 'auto-completion-div',
            completionUrl   : LABKEY.ActionURL.buildURL('security', 'completeUser.api'),
            tagConfig   : {
                tag     : 'input',
                id      : 'cloneUser',
                type    : 'text',
                name    : 'cloneUser',
                disabled: true,
                style   : 'width: 303px;',
                autocomplete : 'off'
            }
        });
    });
</script>

<labkey:form action="<%=buildURL(SecurityController.AddUsersAction.class)%>" method="POST">
    <table><%
            if (getErrors("form").hasErrors());
            { %>
        <tr><td colspan="3"><labkey:errors /></td></tr><%
            }
            if (form.getMessage() != null)
            {
                %><tr><td colspan="3"><div class="labkey-message"><%=form.getMessage()%></div></td></tr><%
            }
        %>
        <tr>
            <td colspan="3">Add new users. Enter one or more email addresses, each on its own line.</td>
        </tr>
        <tr>
            <td colspan="3">
                <textarea name="newUsers" id="newUsers" cols=70 rows=20></textarea><br/><br/>
            </td>
        <tr>
            <td><input type=checkbox id="cloneUserCheck" name="cloneUserCheck" onclick="enableText();">Clone permissions from user:</td>
            <td id="auto-completion-div"></td>
            <td><div id=permissions><a href="#blank" style="display:none" onclick="showUserAccess();">permissions</a></div></td>
        </tr>
        <tr><td colspan="3">
            <input type=checkbox name="sendMail" id="sendMail" checked><label for="sendmail">Send notification emails to all new<%
            String LDAPDomain = AuthenticationManager.getLdapDomain();
            if (LDAPDomain != null && LDAPDomain.length() > 0 && !AuthenticationManager.ALL_DOMAINS.equals(LDAPDomain))
            {
                %>, non-<%=LDAPDomain%><%
            }
            %> users</label><br><br>
        </td></tr>
        <tr>
            <td>
                <labkey:button text="Add Users" />
                <% if (form.getReturnURLHelper() == null) { %>
                <%= button("Done").href(new ActionURL(UserController.ShowUsersAction.class, getContainer())) %>
                <% }
                   else {
                %>
                <%= button("Done").href(form.getReturnURLHelper()) %>
                <% } %>
                <%=generateReturnUrlFormField(form)%>
            </td>
        </tr>
    </table>
    <%
        if (form.getProvider() != null)
        {
            %><input type="hidden" name="provider" id="provider" value=<%=h(form.getProvider())%>><%
        }
        else
        {
            %><input type="hidden" name="provider" id="provider"><%
        }
    %>

</labkey:form>
<script for=window event=onload type="text/javascript">try {document.getElementById("newUsers").focus();} catch(x){}</script>
