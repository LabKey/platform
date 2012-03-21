<%
/*
 * Copyright (c) 2005-2012 LabKey Corporation
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
<table>
    <tr>
        <td colspan=2><b>Formatting Guide</b> (<%=helpLink("wikiSyntax", "more help")%>):</td>
    </tr>
    <tr>
        <td>link to page in this wiki&nbsp;&nbsp;</td>
        <td>[pagename] or [Display text|pagename]</td>
    </tr>
    <tr>
        <td>external link</td>
        <td>http://www.google.com or {link:Display text|http://www.google.com}</td>
    </tr>
    <tr>
        <td>picture</td>
        <td>[attach.jpg] or {image:http://www.website.com/somepic.jpg}</td>
    </tr>
    <tr>
        <td>bold</td>
        <td>**like this**</td>
    </tr>
    <tr>
        <td>italics</td>
        <td>~~like this~~</td>
    </tr>
    <tr>
        <td>bulleted list</td>
        <td>- list item</td>
    </tr>
    <tr>
        <td>numbered List</td>
        <td>1. list item</td>
    </tr>
    <tr>
        <td>line break (&lt;br&gt;)</td>
        <td>\\</td>
    </tr>
</table>