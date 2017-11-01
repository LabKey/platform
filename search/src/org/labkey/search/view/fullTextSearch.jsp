<%
/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.action.SpringActionController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<labkey:form name="fullTextSearch" method="POST">
    <table>
        <tr>
            <td>
                The full-text search feature will search content in all folders where the user has read permissions. There<br>
                may be cases when content that can be read should be excluded from multi-folder searches, for example, if the<br>
                folder contains archived versions of content and you want only the more recent versions of that content to<br>
                appear in search results. Uncheck the box to exclude this folder's content from multi-folder searches.<br>
            </td>
        </tr>
        <tr>
            <td>
                <label>
                    <input type="checkbox" id="searchable" name="searchable"<%=checked(getContainer().isSearchable())%>>
                Include this folder's content in multi-folder search results</label>
                <input type="hidden" name="<%=text(SpringActionController.FIELD_MARKER)%>searchable">
            </td>
        </tr>
    </table>
    <%= button("Save").submit(true) %>
</labkey:form>
