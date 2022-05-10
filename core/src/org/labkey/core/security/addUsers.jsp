<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.settings.LimitActiveUsersSettings" %>
<%@ page import="org.labkey.api.util.Button.ButtonBuilder" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.security.SecurityController.AddUsersAction" %>
<%@ page import="org.labkey.core.security.SecurityController.AddUsersForm" %>
<%@ page import="org.labkey.core.user.UserController.ShowUsersAction" %>
<%@ page import="org.labkey.core.user.UserController.UserUrlsImpl" %>
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
    LimitActiveUsersSettings settings = new LimitActiveUsersSettings();
%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    document.addEventListener("DOMContentLoaded", function() {
        if (LABKEY.ActionURL.getParameter('provider')) {
            document.getElementById('provider').value = LABKEY.ActionURL.getParameter('provider');
        }
    });

    function enableText()
    {
        const checkBoxElem = document.getElementById("cloneUserCheck");
        const textElem = document.getElementById("cloneUser");

        if (checkBoxElem != null && textElem != null)
        {
            const checked = checkBoxElem.checked;

            textElem.disabled = !checked;
            textElem.value = "";

            const showUserAccessElem = document.getElementById("showUserAccessLink");
            if (showUserAccessElem != null)
                showUserAccessElem.style.display = checked ? 'inline' : 'none';
        }
    }

    function showUserAccess()
    {
        var textElem = document.getElementById("cloneUser");
        if (textElem != null)
        {
            if (textElem.value != null && textElem.value.length > 0)
            {
                var target = <%=q(new UserUrlsImpl().getUserAccessURL(ContainerManager.getRoot()).addParameter("renderInHomeTemplate", false).addParameter("newEmail", null))%> + textElem.value;
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

<labkey:form action="<%=urlFor(AddUsersAction.class)%>" method="POST">
    <table>
        <%
            if (getErrors("form").hasErrors());
            {
        %>
        <tr><td><labkey:errors /></td></tr>
        <%
            }
            HtmlString msg = form.getMessage();
            if (!HtmlString.isBlank(msg))
            {
                %><tr><td><div class="labkey-message"><%=msg%></div></td></tr><%
            }
            if (settings.isUserLimit())
            {
        %>
        <tr><td>Number of users that can be added: <%=settings.getRemainingUserCount()%></td></tr>
        <tr><td>&nbsp;</td></tr>
        <%
            }
        %>
        <tr>
            <td>Add new users. Enter one or more email addresses, each on its own line.</td>
        </tr>
        <tr>
            <td>
                <textarea name="newUsers" id="newUsers" cols=70 rows=20></textarea><br/><br/>
            </td>
        <tr>
            <td><input type=checkbox id="cloneUserCheck" name="cloneUserCheck">Clone permissions from user:<span id="auto-completion-div"></span>
            <span id=permissions><a id="showUserAccessLink" href="#" class="labkey-button" style="display:none">permissions</a></span></td>
            <%
                addHandler("cloneUserCheck", "click", "enableText();");
                addHandler("showUserAccessLink", "click", "showUserAccess();");
            %>
        </tr>
        <tr><td>
            <br><input type=checkbox name="sendMail" id="sendMail" checked><label for="sendmail"><%=AuthenticationManager.getStandardSendVerificationEmailsMessage()%></label><br><br>
        </td></tr>
        <tr>
            <td>
<%
    ButtonBuilder submit = button("Add Users");
    if (settings.isUserLimitReached())
    {
        submit.enabled(false);
        submit.tooltip("User limit has been reached");
    }
    else
    {
        submit.submit(true);
    }
%>
                <%=submit%>
                <% if (form.getReturnURLHelper() == null) { %>
                <%= button("Done").href(urlFor(ShowUsersAction.class)) %>
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
<%
    getPageConfig().setFocusId("newusers");
%>
