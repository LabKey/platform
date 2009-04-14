<%
/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
<%@ page import="org.apache.commons.collections.map.MultiValueMap"%>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.pipeline.PipeRoot" %>
<%@ page import="org.labkey.api.pipeline.PipelineProvider" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.pipeline.PipelineController" %>
<%@ page import="java.io.File" %>
<%@ page import="java.net.URI" %>
<%@ page import="java.util.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%
    JspView<PipelineController.PathForm> me = (JspView<PipelineController.PathForm>)HttpView.currentView();
    PipelineController.PathForm form = me.getModelBean();
    ViewContext context = me.getViewContext();
    PipeRoot pipeRoot = (PipeRoot)request.getAttribute("pipeRoot");
    List<PipelineProvider.FileEntry> parents = (List<PipelineProvider.FileEntry>)request.getAttribute("parents");
    List<PipelineProvider.FileEntry> entries = (List<PipelineProvider.FileEntry>)request.getAttribute("entries");
    boolean showCheckboxes = Boolean.TRUE.equals(request.getAttribute("showCheckboxes"));
    String contextPath = request.getContextPath();

    PipelineProvider.FileEntry parent = parents.get(parents.size() - 1);
    for (PipelineProvider.FileAction action : parent.getActions())
    {
        if (action.isRootAction() && action.getDescription() != null)
        {
            %><%=action.getDescription()%><%
            %><p><%=PageFlowUtil.generateButton(action.getLabel(), action.getHref())%></p><%
        }
    }
    for (PipelineProvider.FileAction action : parent.getActions())
    {
        if (action.isRootAction() && action.getDescription() == null)
        {
            %><%=PageFlowUtil.generateButton(action.getLabel(), action.getHref())%>&nbsp;<%
        }
    }%>

<table width="100%">
<tr><td valign="top"><%

    //
    // DIRECTORY TREE
    //
    %><table><%

    for (int i=0 ; i<parents.size() ; i++)
    {
        PipelineProvider.FileEntry entry = parents.get(i);
        boolean bold = i == parents.size()-1;
        %><tr><td colspan=5 style="padding-left:<%=i%>em;"><%
        if (bold) {%><b><%}
        %><a width="100%" href="<%=h(entry.getHref())%>"><img src="<%=h(entry.getImageURL()) %>">&nbsp;<%=f(entry.getLabel())%></a><%
        if (bold) {%></b><%}
        %></td></tr><%
        out.println();
    }
    %></table><%
    //
    // END DIRECTORY
    //


%></td></tr>
<tr><td valign="top"><%

    // separate out the dir actions and file actions
    MultiValueMap dirActions = new MultiValueMap(); 
    ArrayList<PipelineProvider.FileAction> fileActions = new ArrayList<PipelineProvider.FileAction>();
    for (PipelineProvider.FileAction a : parent.getActions())
    {
        if (null != a.getFiles() && a.getFiles().length == 1 && a.getFiles()[0].isDirectory())
            dirActions.put(a.getFiles()[0].toURI(), a);
        else
            fileActions.add(a);
    }

    // Sort the file actions to get a consistent ordering.
    Collections.sort(fileActions, new Comparator<PipelineProvider.FileAction>()
    {
        public int compare(PipelineProvider.FileAction action1, PipelineProvider.FileAction action2)
        {
            if (action1.getFiles() != null && action2.getFiles() != null)
            {
	            Set<File> files1 = new TreeSet<File>(Arrays.asList(action1.getFiles()));
    	        Set<File> files2 = new TreeSet<File>(Arrays.asList(action2.getFiles()));
        	    int i = files1.toString().compareToIgnoreCase(files2.toString());
            	if (i != 0)
                	return i;
            }
            else if (action1.getFiles() != null)
            {
                return -1;
            }
            else if (action2.getFiles() != null)
            {
                return 1;
            }

            return action1.getLabel().compareToIgnoreCase(action2.getLabel());
        }
    });

    //
    // FILES/ACTIONS in current directory
    //
    %><table width="100%"><%

    for (PipelineProvider.FileEntry entry : entries)
    {
        %><tr class="labkey-row"><td style="padding-left: <%= parents.size() %>em;"/><%
        %><td nowrap style="vertical-align: middle;"><a width="100%" href="<%=h(entry.getHref())%>"><img src="<%=h(entry.getImageURL())%>">&nbsp;<%=f(entry.getLabel())%></a></td><%
        Collection<PipelineProvider.FileAction> c = dirActions.getCollection(entry.getURI());
        dirActions.remove(entry.getURI());
        if (c != null)
        {
            %><td style="padding-left: 1em"><div class="labkey-button-bar"><%
            for (PipelineProvider.FileAction action : c)
            {
                %><%=action.getDisplay()%> <%
            }
            %></div></td><%
        }
        %><td width="100%">&nbsp;</td></tr><%
        out.println();
    }

    // should be empty now, but just in case
    for (URI key : (Set<URI>)dirActions.keySet())
        fileActions.addAll(0, dirActions.getCollection(key));

    int i = 0;
    int iForm = 0;
    while (i < fileActions.size())
    {%>
        <tr height="1"><td/><td colspan="2"><hr height="1" /></td></tr>
    <%
        PipelineProvider.FileAction action = fileActions.get(i++);
        if (action.isRootAction())
            continue;

        if (action.getDescription() != null)
        {
            %><tr class="labkey-row"><td colspan="3"><%=action.getDescription()%></td></tr><%
        }

        if (!showCheckboxes) {%><form id="files_form<%=++iForm%>" method="post" action="button_set"><input type="hidden" id="param"><%}
        
        for (File file : action.getFiles())
        {
            out.println();
            %><tr class="labkey-row"><%
            if (showCheckboxes)
            {
                if (action.getFiles().length == 1)
                {
                    %><td style="padding-left: <%= parents.size() %>em"><input type="checkbox" name="file" value="<%=h(file.getName())%>"></td><%
                }
                else
                {
                    %><td>&nbsp;</td><%
                }
            }
            else
            {
                %><td style="padding-left: <%= parents.size() %>em"><input type="checkbox" name="fileInputNames" value="<%=h(file.getName())%>" checked="true" style="display: none;"></td><%
            }
            %><td nowrap style="vertical-align: middle;"><img src="<%=contextPath%><%=Attachment.getFileIcon(file.getName())%>">&nbsp;<%=f(file.getName())%></td><%
            if (file == action.getFiles()[0])
            {
                %><td nowrap style="vertical-align: top; padding-left: 1em" rowspan="<%=action.getFiles().length%>"><%
                %><div class="labkey-button-bar"><%=showCheckboxes ? action.getDisplay() : action.getDisplay(iForm)%><%
                while (i < fileActions.size() && action.hasSameFiles(fileActions.get(i)))
                {
                    action = fileActions.get(i++);
                    %><%=showCheckboxes ? action.getDisplay() : action.getDisplay(iForm)%><%
                }
                %></div></td><%
            }
            %><td width="100%">&nbsp;</td></tr><%
        }
        if (!showCheckboxes) {%></form><%}
    }
    if (entries.isEmpty() && fileActions.isEmpty())
    {
        %><tr><td colspan="3" style="padding-left: <%= parents.size() %>em"><i>no files available for processing in this directory</i></td></tr><%
    }
    else if (!fileActions.isEmpty())
    { %>
        <tr height="1"><td/><td colspan="2"><hr height="1" /></td></tr><%
    }
    %></table><%
    //
    // END FILES
    //

    %></td></tr></table><br><%

// toggle button
    String action = context.getActionURL().getAction();
    boolean files = "files".equals(action) || "files.view".equals(action);
    ActionURL actionToggle = context.cloneActionURL().setAction(files ? "browse" : "files");

    if (files)
    {
        %><%=PageFlowUtil.generateButton("Process and Import Files", actionToggle.getLocalURIString())%>&nbsp;<%
    }
    else if (pipeRoot.getACL().hasPermission(context.getUser(), ACL.PERM_READ))
    {
        %><%=PageFlowUtil.generateButton("Browse All Files", actionToggle.getLocalURIString())%>&nbsp;<%
    }

    if (pipeRoot.getACL().hasPermission(context.getUser(), ACL.PERM_INSERT))
    {
        ActionURL dropUrl = (new ActionURL("ftp","drop",pipeRoot.getContainer())).addParameter("pipeline",StringUtils.defaultString(form.getPath(),"/"));
        %><%=PageFlowUtil.generateButton("Upload Multiple Files", "#uploadFiles", "window.open(" + PageFlowUtil.jsString(dropUrl.getLocalURIString()) + ", '_blank', 'height=600,width=1000,resizable=yes');")
        %>&nbsp;<%
    }
%><%=PageFlowUtil.generateButton("Cancel", "returnToReferer.view")%>

<script type="text/javascript">
function setFormAction(n, url)
{
    var form = document.getElementById("files_form" + n);
    var parts = url.split("?");
    if (parts.length < 2)
        return false;
    var params = parts[1].split("&");
    for (var i = 0; i < params.length; i++)
    {
        var nv = params[i].split("=");
        var param = document.getElementById("param").cloneNode(0);
        param.name = nv[0];
        param.value = unescape(nv[1]);
        form.appendChild(param);
    }
    form.action = parts[0];
    return true;
}

function submitForm(n)
{
    var form = document.getElementById("files_form" + n);
    form.submit();
}
</script>

<%!
String f(String filename)
{
    String h = h(filename);
    return h.replace(" ", "&nbsp;");
}
%>
