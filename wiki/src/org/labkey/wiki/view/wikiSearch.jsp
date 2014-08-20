<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.wiki.model.SearchViewContext" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SearchViewContext> _me = (JspView<SearchViewContext>) HttpView.currentView();
    SearchViewContext _ctx = _me.getModelBean();
%>
<script type="text/javascript">
    function submitSearch()
    {
        var frm = document.getElementById("frmSearch");
        if (frm)
            frm.submit();
    }
</script>
<labkey:form action="<%=_ctx.getSearchUrl()%>" id="frmSearch">
<input type="hidden" name="includeSubfolders" value="1"/>
<table width="100%">
    <tr>
        <td width="99%" align="left">
            <input type="text" name="search" style="width:100%">
        </td>
        <td width="1%">
            <%= button("Search").href("javascript:{}").onClick("submitSearch();") %>
        </td>
    </tr>
</table>
</labkey:form>
