<%@ page import="org.labkey.api.util.AppProps" %>
<%@ page extends="org.labkey.api.security.AddUsersPage" %>
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
                var target = "userAccess.view?newEmail=" + textElem.value;
                window.open(target, "permissions", "height=450,width=500,scrollbars=yes,status=yes,toolbar=no,menubar=no,location=no,resizable=yes");
            }
        }
    }
</script>

<form action="addUsers.post" method=post>
<%if(getMessage() != null) { %>
<%=getMessage()%><br>
<% } %>

    <table border=0 cellspacing=2 cellpadding=0>
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
            String LDAPDomain = AppProps.getInstance().getLDAPDomain();
            if (LDAPDomain != null && LDAPDomain.length() > 0)
            {
                %>, non-<%=LDAPDomain%><%
            }
            %> users</label><br><br>
        </td></tr>
        <tr>
            <td>
                <%=buttonImg("Add Users")%>
                <%=buttonLink("Show Grid", request.getContextPath() + "/User/showUsers.view?.lastFilter=true")%>
            </td>
        </tr>
    </table>
</form>
<script for=window event=onload type="text/javascript">try {document.getElementById("newUsers").focus();} catch(x){}</script>
