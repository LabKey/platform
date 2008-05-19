<%
/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.wiki.WikiRendererType"%>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="java.util.List" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="org.springframework.validation.FieldError" %>
<%@ page import="org.labkey.wiki.model.Wiki" %>
<%@ page import="org.labkey.wiki.WikiController" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.wiki.WikiManager" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ViewContext context = HttpView.currentContext();
    Container c = context.getContainer();
    List<Wiki> pages = WikiManager.getPageList(c);
    boolean hasAdminPermissions = c.hasPermission(context.getUser(), ACL.PERM_ADMIN);

    BindException errors = (BindException) request.getAttribute("_errors");
    Wiki wiki = (Wiki) request.getAttribute("_wiki");
    WikiController.WikiInsertForm form = (WikiController.WikiInsertForm) request.getAttribute("_form");
    boolean useVisualEditor = (Boolean) request.getAttribute("_useVisualEditor");
    boolean _reshow = (Boolean) request.getAttribute("_reshow");
    WikiRendererType currentRendererType = (WikiRendererType) request.getAttribute("_currentRendererType");
%>
<script type="text/javascript">
    LABKEY.requiresScript('tiny_mce/tiny_mce.js');
    LABKEY.requiresScript('tiny_mce/init_tiny_mce.js');
</script>

<script language="javascript" type="text/javascript">
    InitTinyMCE();

    //if this is a reshow, set the dirty bit since the content wasn't actually saved yet
    LABKEY.setDirty(<%=_reshow%>);
</script>

<script type="text/javascript">
function findIdx(options, value)
{
  for (var i = 0; i < options.length; ++i)
  {
    if (options[i].value == value)
      return i;
  }
  return -1;
}


function changeRenderer()
{
    var ropts = document.insertForm.rendererType.options;
    var idx = document.insertForm.rendererType.selectedIndex;
    var oldIdx = findIdx(ropts, "<%=currentRendererType%>");

    var doConfirm = false;
    if (ropts[idx].value == "HTML" && ropts[oldIdx].value == "RADEOX")
      doConfirm = true;

    if (doConfirm)
    {
      var newName = ropts[idx].text;
      var oldName = ropts[oldIdx].text;
      if (!window.confirm("Are you sure you want to change from "+oldName+" to "+newName+"?  "+
                "Some of your markup may be lost."))
      {
          document.insertForm.rendererType.selectedIndex = oldIdx;
          return false;
      }
    }

    //reset the LABKEY dirty state so that the user isn't warned about losing content
    //(which the user won't since we're just doing a reshow)
    LABKEY.setDirty(false);

    document.insertForm.action = 'showInsert.post';
    document.insertForm.reshow.value = 'true';    // don't POST data, just reshow
    document.insertForm.submit();
    return true;
}

</script>
<form name="insertForm" method="post" action="insert.post" enctype="multipart/form-data" onsubmit="return checkSubmit(name.value)">
<input type="hidden" name="redirect" value="<%=PageFlowUtil.filter(form.getRedirect())%>">
<input type="hidden" name="pageId" value="<%=PageFlowUtil.filter(form.getPageId())%>">
<input type="hidden" name="index" value="<%=PageFlowUtil.filter(form.getIndex())%>">
<input type="hidden" name="nextAction" value="false">
<input type="hidden" name="reshow" value="false">
<table><%
    List<ObjectError> mainErrors = errors.getGlobalErrors();
    if (null != mainErrors)
    {
        for (ObjectError e : mainErrors)
        {
            %><tr><td colspan=2><span color=red><%=context.getMessage(e)%></span></td></tr><%
        }
	}
%>
<tr>
  <td colspan=2 align=left>
    <table border=0 cellspacing=2 cellpadding=0>
      <tr>
          <td>
              <input type="image" src="<%=PageFlowUtil.submitSrc()%>" name="insert.post" value="Submit" onClick='LABKEY.setSubmit(true);this.form.action="insert.post";this.form.method="post";' >
              <%
              if (currentRendererType==WikiRendererType.HTML)
              {
                  String toggleText = useVisualEditor ? "Use HTML Source Editor" : "Use Visual HTML Editor";
                  %><input type="image" src="<%=PageFlowUtil.buttonSrc(toggleText)%>" onClick="return toggleVisualEditor();"><%
              }
              %>
          </td>
      </tr>
    </table>
  </td>
</tr>
<tr>
    <td class="ms-searchform">Name</td>
    <td class="normal">
      <input type="text" length="40" name="name" onchange="LABKEY.setDirty(true);return true;" value="<%=h(wiki.getName())%>">
    </td>
  </tr>
<%
    FieldError titleError = errors.getFieldError("title");
    if (null != titleError)
    {
        %><tr><td colspan=2><span color=red><%=context.getMessage(titleError)%></span></td></tr><%
	}
%>
  <tr>
      <td class="ms-searchform">Title</td>
      <td class="normal">
        <input name="title" size="60" type="text" onchange="LABKEY.setDirty(true);return true;" value="<%=h(form.getTitle() != null ? form.getTitle() : form.getName())%>">
    </td>
  </tr>
  <tr>
    <td class="ms-searchform">Parent</td>
    <td class="normal"><select name="parent" onchange="LABKEY.setDirty(true);return true;">
            <option <%= form.getParent() == -1 ? "selected" : "" %> value="-1">[none]</option>
<%
    for (Wiki possibleParent : pages)
        {
        String indent = "";
        int depth = possibleParent.getDepth();
        String parentTitle = possibleParent.latestVersion().getTitle();
        while (depth-- > 0)
          indent = indent + "&nbsp;&nbsp;";
        %><option <%= possibleParent.getRowId() == form.getParent() ? "selected" : "" %> value="<%= possibleParent.getRowId() %>"><%= indent %><%= parentTitle %> (<%= possibleParent.getName() %>)</option><%
        }
%>
    </select>
    </td>
  </tr><%
    FieldError bodyError = errors.getFieldError("body");
    if (null != bodyError)
		{
		%><tr><td colspan=2><span color=red><%=context.getMessage(bodyError)%></span></td></tr><%
		}
  %><tr>
    <td class="ms-searchform">Body</td>

    <%
    if (currentRendererType==WikiRendererType.HTML && useVisualEditor)
    {
        %><td class="normal" style="width:100%;"><textarea class="mceEditor" cols="120" rows="25" name="body" style="width:100%;"><%=h(form.getBody())%></textarea></td><%
    }
    else
    {
        %><td class="normal" style="width:100%;"><textarea class="mceNoEditor" cols="120" rows="25" name="body" onchange="LABKEY.setDirty(true);return true;" style="width:100%;"><%=h(form.getBody())%></textarea></td><%
    }%>

</tr>
  <tr>
      <td class="ms-searchform">Render As</td>
      <td class="normal">
          <select id="rendererType" name="rendererType" onChange="changeRenderer()"><%
                for (WikiRendererType entry : WikiRendererType.values())
                {
                    String value = entry.name();
                    String displayName = entry.getDisplayName();
                    String selected = entry == currentRendererType ? "selected" : "";
                    %><option <%=selected%> value="<%=value%>"><%=displayName%></option><%
                }
          %></select>
      </td>
  </tr>
  <tr>
    <td class="ms-searchform">Attachments</td>
    <td class="normal">
      <table id="filePickerTable"></table>
      <table>
        <tr><td colspan=2><a href="javascript:addFilePicker('filePickerTable', 'filePickerLink')" id="filePickerLink"><img src="<%=request.getContextPath()%>/_images/paperclip.gif">Attach a file</a></td></tr>
      </table>
    </td>
  </tr>
  <tr>
    <td colspan=2 align=left>
      <table id="inputs" border=0 cellspacing=2 cellpadding=0>
        <tr>
            <td>
                <input type="image" src="<%=PageFlowUtil.submitSrc()%>" name="insert.post" value="Submit" onClick='LABKEY.setSubmit(true); this.form.action="insert.post";this.form.method="post";' >
                <%
                if (currentRendererType==WikiRendererType.HTML)
                {
                    String toggleText = useVisualEditor ? "Use HTML Source Editor" : "Use Visual HTML Editor";
                    %><input type="image" src="<%=PageFlowUtil.buttonSrc(toggleText)%>" onClick="return toggleVisualEditor();"><%
                }
                %>
            </td>
        </tr>
      </table>
    </td>
  </tr>
</table>
<script type="text/javascript">
//page names are always checked in lowercase, since they are case insensitive
existingWikiPages = [<% for (Wiki p : pages) out.print(PageFlowUtil.jsString(p.getName().toLowerCase()) + ","); %>];
hasAdminPermissions = <%=hasAdminPermissions%>;

function checkSubmit(name)
{
    return LABKEY.submit = checkWikiName(name);
}

function checkWikiName(name)
    {
    if (document.insertForm.nextAction.value != 'false') {
        return true;
    }

    if (!name)
        {
        window.alert("Please choose a name for this wiki page.");
        return false;
        }
    if (!hasAdminPermissions && name.substr(0, 1) == "_")
    {
        window.alert("Wiki names starting with an underscore are reserved for administrators.");
        return false;
    }
    for (i = 0 ; i < existingWikiPages.length ; i++)
        {
        if (name.toLowerCase() == existingWikiPages[i])
            {
            window.alert("Page named '" + name + "' already exists.  Please choose a new name.");
            return false;
            }
        }
    return true;
    }
</script>
</form>
<script type="text/javascript">
var wikiEditor = {
    origBody:"",
    useVisualEditor:<%=useVisualEditor?"true":"false"%>,
    hasScript:false
};
wikiEditor.origBody = getWikiBody().value;
wikiEditor.isDirty = function()
{
    //if reshow is true, just report false since we're just changing renderers
    if("true" == document.insertForm.reshow.value)
        return false;

    var inst = tinyMCE.getInstanceById("mce_editor_0");
    if (inst && inst.isDirty())
        return true;
    var mceBody = getWikiBody();
    if (mceBody)
        return mceBody.value != wikiEditor.origBody;
    return false;
}

// I don't think YAHOO.util.Event.addListener() will work properly for onbeforeunload
//YAHOO.util.Event.addListener(window, "beforeunload", wikiEditor.beforeunload);
window.onbeforeunload = LABKEY.beforeunload(wikiEditor.isDirty);

function toggleVisualEditor()
{
    if (!wikiEditor.useVisualEditor && wikiEditor.hasScript)
    {
        if (!window.confirm("Warning: the visual editor will strip forms and script from your HTML source.  Continue?"))
            return false;
    }
    document.insertForm.nextAction.value = wikiEditor.useVisualEditor ? 'toggleVisualEditorOff' : 'toggleVisualEditorOn';
    document.insertForm.reshow.value = 'true';    // don't POST data, just reshow
    <%if ("insert".equals(context.getActionURL().getAction()) || "showInsert".equals(context.getActionURL().getAction()))
    {
        %>document.insertForm.action='insert.post';<%
    }
    else
    {
        %>document.insertForm.action='update.post?name=' + <%=PageFlowUtil.jsString(StringUtils.trimToEmpty(wiki.getName()))%>;<%
    }%>
    LABKEY.setSubmit(true);
    return true;
}

function getWikiBody()
{
    return document.getElementsByName("body")[0];
}
</script>