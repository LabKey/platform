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
<%@ page import="org.labkey.core.authentication.test.TestSsoController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<p>SSO Test Authentication: Type an email address below to "authenticate" as that user</p>
<form <%=formAction(TestSsoController.ValidateAction.class, Method.Post)%>>
    <table>
        <tr>
            <td valign="top"><input type="text" name="email" value="" autofocus></td>
        </tr>
    </table>
    <input type="submit" name="Authenticate" value="Authenticate">
</form>