<%
/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.issue.IssuesController"%>
<%@ page import="org.labkey.issue.IssuesController.KeywordPicker" %>
<%@ page import="org.labkey.issue.model.KeywordManager.Keyword" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<List<KeywordPicker>> me = (HttpView<List<KeywordPicker>>) HttpView.currentView();
    List<KeywordPicker> keywordPickers = me.getModelBean();
    Map<String, String> delete = new HashMap<String, String>();
    delete.put("title", "Delete this keyword");
    Map<String, String> clear = new HashMap<String, String>();
    clear.put("title", "Clear the default values");
    Map<String, String> set = new HashMap<String, String>();
    set.put("title", "Set as the default value");
%>
<table>
<tr><%
    for (KeywordPicker kwp : keywordPickers)
    {
        String formId = "form" + kwp.type.getColumnName();
%>
    <!-- <%=h(kwp.type.getColumnName())%> -->
    <td style="vertical-align:top">
    <div class="labkey-form-label"><b><%=h(kwp.name)%> Options</b></div>
    <form id="<%=h(formId)%>" method="POST" action="<%=h(buildURL(IssuesController.DeleteKeywordAction.class))%>">
<%
    if (kwp.keywords.isEmpty())
    {
        %><i>none set</i><br><%
    }
    else
    {
        %><table><%

        for (Keyword keyword : kwp.keywords)
        {
            boolean selected = keyword.isDefault();
%>
        <tr><td><%=selected ? "<b>" + h(keyword.getKeyword()) + "</b>" : h(keyword.getKeyword())%></td><td><%=textLink("delete", "javascript:callAction('deleteKeyword', '" + formId + "', " + PageFlowUtil.jsString(keyword.getKeyword()) + ")", "", null, delete)%>&nbsp;<%
            if (selected)
            {
                %><%=textLink("clear", "javascript:callAction('clearKeywordDefault', '" + formId + "', " + PageFlowUtil.jsString(keyword.getKeyword()) + ")", "", null, clear)%><%
            }
            else
            {
                %><%=textLink("set", "javascript:callAction('setKeywordDefault', '" + formId + "', " + PageFlowUtil.jsString(keyword.getKeyword()) + ")", "", null, set)%><%
            }%></td></tr><%
        }

        out.println("\n    </table>");
    }
%>    <input type="hidden" name="keyword" value=""><input type="hidden" name="type" value="<%=kwp.type.getOrdinal()%>">
    </form>
    </td>
<%
    }
%>
</tr>
<tr>
<%
    for (KeywordPicker kwp : keywordPickers)
    {
%>
<td align="center">
    <form method="POST" name="add<%=h(kwp.type.getColumnName())%>" action="<%=h(buildURL(IssuesController.AddKeywordAction.class))%>">
    <input name="keyword" value=""><br>
        <%=generateSubmitButton("Add " + kwp.name)%><br>
    <input type="hidden" name="type" value="<%=kwp.type.getOrdinal()%>">
    </form>
</td><%
    }
%>
</tr>
</table>

<script type="text/javascript">
function callAction(action, form, word)
{
    f = document.forms[form];
    f['keyword'].value = word;
    if (-1 != window.location.pathname.indexOf('issues-') && -1 == action.indexOf('-'))
        action = "issues-" + action;
    f.action = action + ".post";
    f.submit();
}
</script>
