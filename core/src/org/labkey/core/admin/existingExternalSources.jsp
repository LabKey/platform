<%
/*
 * Copyright (c) 2019 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.permissions.ApplicationAdminPermission" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.admin.AdminController.ExternalSourcesForm" %>
<%@ page import="org.labkey.core.security.AllowedExternalResourceHosts.AllowedHost" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    Container c = getContainer();
    boolean isTroubleshooter = !c.hasPermission(getUser(), ApplicationAdminPermission.class);
%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    let _formExisting;

    LABKEY.Utils.onReady(function() {
        _formExisting = new LABKEY.Form({formElement: 'form-existingValues'});
    });

    function deleteExisting(valueToDelete) {
        document.getElementById("delete").value = true;
        document.getElementById("saveAll").value = false;
        document.getElementById("existingValue").value = valueToDelete;
        document.forms["existingValues"].submit();
    }

    function saveAll() {
        //clicking on save will save all the values - changed and unchanged values
        let num = 1;
        let directiveId = "directive" + num;
        let hostId = "host" + num;
        let values = "";

        while (null != document.getElementById(directiveId))
        {
            values += (document.getElementById(directiveId).getAttribute('data-directive') + "|" + document.getElementById(hostId).value) + "\n";
            num++;
            directiveId = "directive" + num;
            hostId = "host" + num;
        }

        document.getElementById("saveAll").value = true;
        document.getElementById("existingValues").value = values;
        document.forms["existingValues"].submit();
        _formExisting.setClean();
    }
</script>

<labkey:form name="existingValues" id="form-existingValues" method="post">

<%
    ExternalSourcesForm bean = (ExternalSourcesForm) HttpView.currentModel();
    List<AllowedHost> existingAllowedHosts = bean.getSavedAllowedHosts();
    existingAllowedHosts.sort(Comparator.comparing(AllowedHost::directive).thenComparing(AllowedHost::host));
%>
    <table class="labkey-data-region-legacy labkey-show-borders">
<%
    if (existingAllowedHosts.isEmpty())
    {
%>
        <thead><tr><th colspan="2">No External Resource Hosts have been configured.</th></tr></thead>
<%
    }
    else
    {
%>
        <thead><tr><th>Directive</th><th>Host</th></tr></thead>
<%
        int num = 1;
        for (AllowedHost sub : existingAllowedHosts) {
            String directiveId = "directive" + num;
            String hostId = "host" + num;
%>
        <tr>
            <td><input type="text" id="<%=h(directiveId)%>" name="<%=hname(directiveId)%>" value="<%=h(sub.directive().getCspDirective())%>" data-directive="<%=sub.directive()%>" size="20" disabled/></td>
            <td><input type="text" id="<%=h(hostId)%>" name="<%=hname(hostId)%>" value="<%= h(sub.host())%>" size="80"<%=disabled(isTroubleshooter)%>/></td>

            <td><%=isTroubleshooter ?
                HtmlString.EMPTY_STRING :
                button("Delete")
                    .primary(true)
                    .onClick("return deleteExisting(" +
                        q(sub.directive() + "|" + sub.host()) + // Using | separator is safe because the directive name never contains |
                        ");") %>

            </td>
        </tr>
<%
            num++;
        }

        if (!existingAllowedHosts.isEmpty())
        {
%>
    </table>
    <input type="hidden" id="delete" name="delete" value="false" />
    <input type="hidden" id="existingValue" name="existingValue" value="" />
    <input type="hidden" id="existingValues" name="existingValues" value="" />
    <br/><input type="hidden" id="saveAll" name="saveAll"><%=isTroubleshooter ? button("Done").href(urlProvider(AdminUrls.class).getAdminConsoleURL()) : button("Save").primary(true).onClick("return saveAll();")%>
<%
        }
    }
%>
</labkey:form>
