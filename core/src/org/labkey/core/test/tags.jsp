<%
/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ taglib prefix="s" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<form:form commandName="form" enctype="multipart/form-data">
    <form:errors path="*" cssClass="error"></form:errors>
    <table>
        <tr><td colspan="2"><form:errors path="a" cssClass="error"/></td></tr>
        <tr><td>a</td><td><form:checkbox path="a"></form:checkbox></td></tr>
        <tr><td colspan="2"><form:errors path="b" cssClass="error"/></td></tr>
        <tr><td>b</td><td><form:input path="b"></form:input></td></tr>
        <tr><td colspan="2"><form:errors path="c" cssClass="error"/></td></tr>
        <tr><td>c</td><td><form:input path="c"></form:input></td></tr>
        <tr><td colspan="2"><form:errors path="int" cssClass="error"/></td></tr>
        <tr><td>int</td><td><form:input path="int"></form:input></td></tr>
        <tr><td colspan="2"><form:errors path="positive" cssClass="error"/></td></tr>
        <tr><td>Positive Number</td><td><form:input path="positive"></form:input></td></tr>
        <tr><td colspan="2"><form:errors path="required" cssClass="error"/></td></tr>
        <tr><td>Required String</td><td><form:input path="required"></form:input></td></tr>
        <tr><td>Text</td><td><form:textarea path="text" rows="12" cols="60"></form:textarea></td></tr>
        <tr><td>x</td><td><form:input path="x"></form:input></td></tr>
        <tr><td>y</td><td><form:input path="y"></form:input></td></tr>
        <tr><td>z</td><td><form:input path="z"></form:input></td></tr>
        <tr><td>file</td><td><input type="file" name="file"></td></tr>
    </table>
    <%= button("Submit").submit(true) %>
</form:form>
   
<br>
<b>todo</b>
<ul>
    <li>use custom field marker for checkbox @ instead of _</li>
    <li>select 'global' errors at top of form</li>
</ul>