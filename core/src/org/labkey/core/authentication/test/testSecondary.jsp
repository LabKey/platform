<%
/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    String message1 = "Secondary Authentication";
    String email = (String)getModelBean();
    String message2 = "Is " + h(email) + " really you?"; //testing for this string in automated test 'SecondaryAuthenticationTest'
%>
<p><%=h(message1)%></p>
<p><%=h(message2)%></p>

<labkey:form method="POST">
    <table>
        <tr>
            <td valign="top"><input type="radio" name="valid" value="1"></td>
            <td>Yes!</td>
        </tr>
        <tr>
            <td valign="top"><input type="radio" name="valid" value="0" checked></td>
            <td>No</td>
        </tr>
    </table>
    <input type="submit" name="TestSecondary" value="TestSecondary">
</labkey:form>