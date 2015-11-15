<%
    /*
     * Copyright (c) 2015 LabKey Corporation
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
<%
    String returnUrl = getViewContext().getActionURL().getParameter("returnUrl");
%>

<labkey:errors/>
<labkey:form action="" method="POST">

    <table>
        <tr><td>Name:&nbsp;</td><td><input type="text" name="name"></td></tr>
        <tr><td>Description:&nbsp;</td><td><input type="text" name="description"></td></tr>
        <tr><td>Name Expression:&nbsp;</td><td><input type="text" name="nameExpression"></td></tr>
        <tr><td>Material Source ID:&nbsp;</td><td><input type="text" name="materialSourceId"></td></tr>
        <tr><td></td><td>
            <%=button("Create").submit(true)%>&nbsp;
            <%=button("Cancel").href(returnUrl)%>
        </td></tr>
    </table>
</labkey:form>