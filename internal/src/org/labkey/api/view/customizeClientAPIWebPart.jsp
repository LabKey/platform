<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Portal.WebPart> me = (JspView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart bean = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    ActionURL postUrl = new ActionURL("Project", "customizeWebPart.post", ctx.getContainer());

    String defaultContent = bean.getPropertyMap().get(ClientAPIWebPartFactory.DEFAULT_CONTENT_KEY);

    String content = bean.getPropertyMap().get(ClientAPIWebPartFactory.CONTENT_KEY);
    if (content == null)
        content = "";
%>
<script type="text/javascript">
    LABKEY.requiresClientAPI();

    function preview()
    {
        var sourceElem = Ext.get("content");
        var targetElem = Ext.get("preview");
        targetElem.update(sourceElem.getValue(), true);
    }

    function reset()
    {
        var sourceElem = Ext.get("defaultContent");
        var contentTextArea = document.getElementById("content");
        contentTextArea.value = sourceElem.getValue();
    }
</script>

<form action="<%=postUrl%>" method="post">
    <input type="hidden" name="pageId" value="<%=bean.getPageId()%>">
    <input type="hidden" name="index" value="<%=bean.getIndex()%>">
<table>
        <tr>
            <td>
                <textarea name="<%=ClientAPIWebPartFactory.CONTENT_KEY%>" id="content" rows="30" cols="80"><%=content%></textarea>
            </td>
        </tr>
    <tr>
        <td>
            <%=generateSubmitButton("Save & Close")%>
            <%=PageFlowUtil.generateButton("Preview", "", "preview()")%>
            <%=PageFlowUtil.generateButton("Reset", "", "reset()")%>
            <%=generateButton("Cancel", "begin.view")%>
        </td>
    </tr>
</table>
    
<% WebPartView.startTitleFrame(out, "Preview:");%>
<div id="preview"><%=content%></div>
<% WebPartView.endTitleFrame(out);%>

<div id="defaultContent" style="visibility:hidden;"><%=PageFlowUtil.filter(defaultContent)%></div>
</form>