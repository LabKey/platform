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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.wiki.WikiController" %>
<%@ page import="org.labkey.wiki.WikiController.ManageAction.ManageBean" %>
<%@ page import="org.labkey.wiki.model.Wiki" %>
<%@ page import="org.labkey.wiki.model.WikiTree" %>
<%@ page import="org.springframework.validation.Errors" %>
<%@ page import="org.springframework.validation.FieldError" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    ManageBean bean = ((HttpView<ManageBean>)HttpView.currentView()).getModelBean();
    ViewContext context = getViewContext();
    Container c = getContainer();
    Errors errors = getErrors("form");
    Wiki wiki = bean.wiki;
%>
<script type="text/javascript">
    function saveWikiList(listName, targetName)
    {
        var wikiSelect = document.manage[listName];
        var wikiList = "";

        for (var i = 0; i < wikiSelect.length; i++)
        {
            wikiList += wikiSelect.item(i).value;
            if (i < wikiSelect.length - 1)
                wikiList += ",";
        }

        document.manage[targetName].value = wikiList;
    }

    function orderModule(listName, down, targetName)
    {
        var wikiSelect = document.manage[listName];
        var selWikiIndex = wikiSelect.selectedIndex;

        if (selWikiIndex != -1)
        {
            var swapWiki = null;

            if (selWikiIndex > 0 && down == 0)
            {
                swapWiki = wikiSelect.item(selWikiIndex - 1);
                wikiSelect.selectedIndex--;
            }
            else if (selWikiIndex < wikiSelect.length-1 && down == 1)
            {
                swapWiki = wikiSelect.item(selWikiIndex + 1);
                wikiSelect.selectedIndex++;
            }

            if (swapWiki != null)
            {
                var selWiki = wikiSelect.item(selWikiIndex);
                var selText = selWiki.text;
                var selValue = selWiki.value;
                selWiki.text = swapWiki.text;
                selWiki.value = swapWiki.value;
                swapWiki.text = selText;
                swapWiki.value = selValue;
                saveWikiList(listName, targetName);
                document.manage.nextAction.value = <%= PageFlowUtil.jsString(WikiController.NextAction.manage.name()) %>;
                return false;
            }
        }
        else
        {
            alert("Please select a page first.");
        }

        return false;
    }
</script>



<labkey:form method="post" name="manage" action="<%=h(buildURL(WikiController.ManageAction.class))%>" enctype="multipart/form-data" onsubmit="return checkWikiName(name.value)">
<input type="hidden" name="containerPath" value="<%=h(c.getPath())%>">

<table><tr>
  <td><table class="lk-fields-table">
<%
    FieldError nameError = errors.getFieldError("name");
	if (null != nameError)
    {
		%><tr><td colspan=2><span class="labkey-error"><%=h(context.getMessage(nameError))%></span></td></tr><%
    }
  %>
    <tr>
      <td class='labkey-form-label'><label for="name">Name</label></td>
      <td><input type="text" style="width:420px" id="name" name="name" value="<%=h(wiki.getName()) %>"></td>
    </tr>
    <tr>
      <td></td><td style="width:420px">WARNING: Changing a page's name will break any links to the page.</td>
    </tr>
    <tr>
      <td class='labkey-form-label'><label for="title">Title</label></td>
      <td><input type="text" style="width:420px" name="title" id="title" value="<%=h(wiki.getLatestVersion().getTitle()) %>"></td>
    </tr>
    <tr>
      <td class='labkey-form-label'><label for="parent">Parent</label></td>
      <td><select name="parent" id="parent" style="width:420px" onChange="document.manage.nextAction.value = <%= PageFlowUtil.jsString(WikiController.NextAction.manage.name()) %>; submit();">
        <option<%=selected(wiki.getParent() == -1)%> value="-1">[none]</option><%

    for (WikiTree possibleParent : bean.possibleParents)
    {
        String indent = "";
        int depth = possibleParent.getDepth();
        while (depth-- > 0)
            indent = indent + "&nbsp;&nbsp;";
%>
        <option<%=selected(possibleParent.getRowId() == wiki.getParent())%> value="<%= possibleParent.getRowId() %>"><%=text(indent)%><%=possibleParent.getTitle() %> (<%= possibleParent.getName() %>)</option><%
    }
%>
      </select></td>
    </tr>
    <tr>
        <td class='labkey-form-label'><label for="shouldIndex">Index</label></td>
        <td><input type="checkbox" name="shouldIndex" id="shouldIndex"<%=checked(wiki.isShouldIndex())%>></td>
    </tr>
    <tr>
        <td class='labkey-form-label'><label for="siblings">Sibling Order</label></td>
        <td>
            <table>
                <tr>
                    <td><select name="siblings" id="siblings" size="10" style="width: 320px;"><%
                         for (WikiTree sibling : bean.siblings)
                        {
                    %>
                        <option<%=selected(sibling.getRowId() == wiki.getRowId())%> value="<%= sibling.getRowId() %>"><%= sibling.getTitle() %> (<%= sibling.getName() %>)</option><%
                            }
                        %>
                    </select></td>
                    <td valign="top" >
                        <%= button("Move Up").attributes("style='width:100px'").submit(true).onClick("return orderModule('siblings', 0, 'siblingOrder', " + PageFlowUtil.jsString(WikiController.NextAction.manage.name()) + ")") %>
                        <br/>
                        <%= button("Move Down").attributes("style='width:100px'").submit(true).onClick("return orderModule('siblings', 1, 'siblingOrder', "  + PageFlowUtil.jsString(WikiController.NextAction.manage.name()) + ")") %>
                    </td>
                </tr>
            </table>
            <input type="hidden" name="siblingOrder" value="">
        </td>
    </tr>
    <%
        if (bean.showChildren && wiki.hasChildren())
        {
    %>
    <tr>
        <td class='labkey-form-label'><label for="children">Child Order</label></td>
        <td><table>
            <tr>
                <td>
                    <select name="children" id="children" size="10">
                        <%
                            for (Wiki child : wiki.children())
                            {
                        %>
                        <option value="<%= child.getRowId() %>"><%= child.getLatestVersion().getTitle() %> (<%= child.getName() %>)</option><%
                        }
                    %>
                   </select>
                </td>
                <td valign="top">
                    <%= button("Move Up").attributes("style='width:200px'").submit(true).onClick("return orderModule('children', 0, 'childOrder')")%>
                    <br><br>
                    <%= button("Move Down").submit(true).onClick("return orderModule('children', 1, 'childOrder')")%>
                </td>
            </tr>
        </table>
            <input type="hidden" name="childOrder" value="">
        </td>
    </tr>
    <%
        }
    %>
  </table></td>
</tr></table>

<input type="hidden" name="originalName" value="<%= wiki.getName() %>">
<input type="hidden" name="rowId" value="<%= wiki.getRowId() %>">
<input type="hidden" name="nextAction" value="">
<%= button("Save").submit(true).onClick("document.manage.nextAction.value = " + PageFlowUtil.jsString(WikiController.NextAction.page.name()) + "; return true;").attributes("title=\"Save Changes\"")%>
<%= PageFlowUtil.button("Delete").href(new ActionURL(WikiController.DeleteAction.class, c).addParameter("name", wiki.getName())) %>
<%= button("Edit Content").submit(true).onClick("document.manage.nextAction.value = " + PageFlowUtil.jsString(WikiController.NextAction.edit.name()) + "; return true;").attributes("title=\"Edit Content and Attachments\"")%>

<script type="text/javascript">
    existingWikiPages = [<% for (String name : bean.pageNames) out.print(text(PageFlowUtil.jsString(name) + ",")); %>];

    function checkWikiName(name)
    {
        if (!name)
        {
            window.alert("Please choose a name for this wiki page.");
            return false;
        }

        return true;
    }
</script>
</labkey:form>
