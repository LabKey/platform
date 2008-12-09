<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.issue.IssuesController.KeywordPicker"%>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<List<KeywordPicker>> me = (HttpView<List<KeywordPicker>>) HttpView.currentView();
    List<KeywordPicker> keywordPickers = me.getModelBean();
%>
<table>
<tr><%
    for (KeywordPicker kwp : keywordPickers)
    {
%>
    <!--<%=kwp.plural%>-->
    <td style="vertical-align:top">
    <div class="labkey-form-label"><b><%=kwp.plural%></b></div>
    <form id="form<%=kwp.plural%>" method="POST" action="deleteKeyword.post">
<%
    if (kwp.keywords.length == 0)
    {
        out.println("    <i>no " + kwp.plural + "</i><br>");
    }
    else
    {
        out.print("    <table>");

        for (IssueManager.Keyword keyword : kwp.keywords)
        {
            boolean selected = keyword.isDefault();
%>
        <tr><td><%=selected ? "<b>" + h(keyword.getKeyword()) + "</b>" : h(keyword.getKeyword())%></td><td>[<a href="javascript:callAction('deleteKeyword','form<%=kwp.plural%>',<%=h(PageFlowUtil.jsString(keyword.getKeyword()))%>)" title="Delete this keyword">delete</a>]&nbsp;<%
            if (selected)
            {
                %>[<a href="javascript:callAction('clearKeywordDefault','form<%=kwp.plural%>',<%=h(PageFlowUtil.jsString(keyword.getKeyword()))%>)" title="Clear the default value">clear</a>]<%
            }
            else
            {
                %>[<a href="javascript:callAction('setKeywordDefault','form<%=kwp.plural%>',<%=h(PageFlowUtil.jsString(keyword.getKeyword()))%>)" title="Set as the default value">set</a>]<%
            }%></td></tr><%
        }

        out.println("\n    </table>");
    }
%>    <input type="hidden" name="keyword" value=""><input type="hidden" name="type" value="<%=kwp.type%>">
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
<td>
    <form method="POST" name="add<%=kwp.name%>" action="addKeyword.post">
    <input name="keyword" value=""><br>
        <%=PageFlowUtil.generateSubmitButton("Add " + kwp.name)%><br>
    <input type="hidden" name="type" value="<%=kwp.type%>">
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
    f.action = action + ".post";
    f.submit();
}
</script>
