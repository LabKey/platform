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
<%@ page import="org.labkey.core.admin.AdminController.AllowListForm" %>
<%@ page import="org.labkey.core.admin.AdminController.DeleteAllValuesAction" %>
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
        var num = 1;
        var inputNameExisting = "existingValue" + num;
        var values = "";

        while (null != document.getElementById(inputNameExisting))
        {
            values += (document.getElementById(inputNameExisting).value + "\n");
            num++;
            inputNameExisting = "existingValue" + num;
        }

        document.getElementById("saveAll").value = true;
        document.getElementById("existingValues").value = values;
        document.forms["existingValues"].submit();
        _formExisting.setClean();
    }
</script>

<labkey:form name="existingValues" id="form-existingValues" method="post">
    <%
        AllowListForm bean = (AllowListForm) HttpView.currentModel();
        List<String> exitingValues = bean.getExistingValuesList();
    %>
    <table class="labkey-data-region-legacy labkey-show-borders">
        <tr>
            <th><%=h(bean.getTypeEnum().getTitle() + "s")%></th>
            <th></th>
        </tr>
        <% if (exitingValues.isEmpty()) { %>
            <tr><td colspan="2">No <%=h(bean.getTypeEnum().getTitle())%>s have been configured.</td></tr>
        <% } %>

        <%
            int num = 1;
            for (String value : exitingValues) {
                String inputNameExisting = "existingValue" + num;
        %>
        <tr>
            <td><input type="text" id="<%=h(inputNameExisting)%>" name="<%=hname(inputNameExisting)%>" value="<%=h(value)%>" size="80"<%=disabled(isTroubleshooter)%>/></td>
            <td><%=isTroubleshooter ? HtmlString.EMPTY_STRING : button("Delete").primary(true).onClick("return deleteExisting(\"" + h(value) + "\");") %></td>
        </tr>
        <%
            num++;
            }
        %>
    </table>
        <% if (!exitingValues.isEmpty()) { %>
            <input type="hidden" id="delete" name="delete" value="false" />
            <input type="hidden" id="existingValue" name="existingValue" value="" />
            <input type="hidden" id="existingValues" name="existingValues" value="" />
            <tr>
                <td></td>
                <td><br/>
                    <input type="hidden" id="saveAll" name="saveAll">
                    <%=isTroubleshooter ? button("Done").href(urlProvider(AdminUrls.class).getAdminConsoleURL()) : button("Save").primary(true).onClick("return saveAll();")%>
                    <%=isTroubleshooter ? HtmlString.EMPTY_STRING :
                            button("Delete All")
                                .href(urlFor(DeleteAllValuesAction.class)
                                .addParameter("type", bean.getTypeEnum().name()))
                                // Can't use LABKEY.Utils.confirmAndPost() below because it always returns false, and we need to preserve the dirty state in the cancel case
                                .onClick(
                                    "if (confirm(" + q("Are you sure you want to delete all " + bean.getTypeEnum().getTitle() + "s") + "))\n" +
                                    "{\n" +
                                    "    _formExisting.setClean();\n" +
                                    "    LABKEY.Utils.postToAction(this.href);\n" +
                                    "}\n" +
                                    "return false;"
                                )%>
                </td>
            </tr>
        <% } %>
</labkey:form>
