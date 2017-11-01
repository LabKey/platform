<%
/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.exp.list.ListService" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<labkey:form action="" name="import" enctype="multipart/form-data" method="post">
<table cellpadding=0>
    <%=formatMissedErrorsInTable("form", 2)%>
<tr><td>
You can import a list archive to create and populate lists in this folder.  A list archive is a .lists.zip file that
conforms to the LabKey list export format.  A list archive is typically created using the list export feature.  Using
export and import, lists can be moved from one server to another or a folder can be initialized with standard lists or
list templates.<%
    Container c = getContainer();

    if (!ListService.get().getLists(c).isEmpty())
    {
%><p>Warning: Existing lists will be replaced by lists in the archive with the same name; this could result in data loss and cannot be undone.</p>
<%
    }
%>
<p>For more information about exporting and importing lists, see <%=helpLink("exportImportLists", "the list documentation")%>.</p>
</td></tr>
<tr>
    <td><input type="file" name="listZip" size="50" style="border: none; background-color: transparent;"></td>
</tr>
<tr>
    <td><br/><%= button("Import List Archive").submit(true) %></td>
</tr>

</table>
</labkey:form>
