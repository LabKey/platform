<%@ page import="org.apache.commons.collections.map.MultiValueMap"%>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.pipeline.PipeRoot" %>
<%@ page import="org.labkey.api.pipeline.PipelineProvider" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.util.AppProps" %>
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
        if (action.isRootAction() &&action.getDescription() != null)
        {
            %><%=action.getDescription()%><%
            %><p><a href="<%=h(action.getHref())%>"><img src="<%=PageFlowUtil.buttonSrc(action.getLabel())%>" border="0"/></a></p><%
        }
    }
    for (PipelineProvider.FileAction action : parent.getActions())
    {
        if (action.isRootAction() && action.getDescription() == null)
        {
            %><a href="<%=h(action.getHref())%>"><img src="<%=PageFlowUtil.buttonSrc(action.getLabel())%>" border="0"/></a>&nbsp;<%
        }
    }%>

<table cellspacing="0" cellpadding="0" style="width:100%; height:120px">
<tr><td valign="top"><%

    //
    // DIRECTORY TREE
    //
    %><table  cellspacing="0" cellpadding="2" style="width:240px;"><%

    for (int i=0 ; i<parents.size() ; i++)
    {
        PipelineProvider.FileEntry entry = parents.get(i);
        boolean bold = i == parents.size()-1;
        %><tr><td colspan=5 class="normal" style="padding-left:<%=i*10%>;"><%
        if (bold) {%><b><%}
        %><a style="width:100%" href="<%=h(entry.getHref())%>"><img border="0" src="<%=h(entry.getImageURL()) %>">&nbsp;<%=f(entry.getLabel())%></a><%
        if (bold) {%></b><%}
        %></td></tr><%
        out.println();
    }
    %></table><%
    //
    // END DIRECTORY
    //


%></td></tr>
<tr><td><%
  WebPartView.startTitleFrame(out, "File listing", null, "100%", null);    
  WebPartView.endTitleFrame(out);
%>
</td></tr>
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
    Collections.sort(fileActions, new Comparator<PipelineProvider.FileAction>(){
        public int compare(PipelineProvider.FileAction action1, PipelineProvider.FileAction action2)
        {
            int i = action1.getFiles()[0].getName().compareToIgnoreCase(action2.getFiles()[0].getName());
            if (i != 0)
                return i;
            return action1.getLabel().compareToIgnoreCase(action2.getLabel());
        }
    });

    //
    // FILES/ACTIONS in current directory
    //
    %><table cellspacing="0" cellpadding="2" style="width:100%;"><%

    int row = 0;
    for (PipelineProvider.FileEntry entry : entries)
    {
        String color = (0 == (row++)%2) ? "#ffffff" : "#ffffff";
        %><tr style="background-color:<%=color%>"><td>&nbsp;</td><%
        %><td nowrap class="normal" style="vertical-align: middle;"><a style="width:100%" href="<%=h(entry.getHref())%>"><img border="0" src="<%=h(entry.getImageURL())%>">&nbsp;<%=f(entry.getLabel())%></a></td><%
        Collection<PipelineProvider.FileAction> c = dirActions.getCollection(entry.getURI());
        dirActions.remove(entry.getURI());
        if (c != null)
        {
            %><td><table cellspacing="0"><tr><%
            for (PipelineProvider.FileAction action : c)
            {
                %><td nowrap><%=action.getDisplay()%></td><%
            }
            %></tr></table></td><%
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
    {
        PipelineProvider.FileAction action = fileActions.get(i++);
        if (action.isRootAction())
            continue;

        String color = (0 == (row++)%2) ? "#ffffff" : "#ffffff";
        if (action.getDescription() != null)
        {
            %><tr style="background-color:<%=color%>"><td colspan="3"><%=action.getDescription()%></td></tr><%
        }

        if (!showCheckboxes) {%><form id="files_form<%=++iForm%>" method="post" action="button_set"><input type="hidden" id="param"><%}
        
        for (File file : action.getFiles())
        {
            out.println();
            %><tr style="background-color:<%=color%>"><%
            if (showCheckboxes)
            {
                if (action.getFiles().length == 1)
                {
                    %><td><input type="checkbox" name="file" value="<%=h(file.getName())%>"></td><%
                }
                else
                {
                    %><td>&nbsp;</td><%
                }
            }
            else
            {
                %><td><input type="checkbox" name="fileInputNames" value="<%=h(file.getName())%>" checked="true" style="visibility: hidden;"></td><%
            }
            %><td nowrap class="normal" style="vertical-align: middle;"><img border="0" src="<%=contextPath%><%=Attachment.getFileIcon(file.getName())%>">&nbsp;<%=f(file.getName())%></td><%
            if (file == action.getFiles()[0])
            {
                %><td nowrap class="normal" style="vertical-align: top;" rowspan="<%=action.getFiles().length%>"><%
                %><table cellspacing="0"><tr><td><%=showCheckboxes ? action.getDisplay() : action.getDisplay(iForm)%></td><%
                while (i < fileActions.size() && action.hasSameFiles(fileActions.get(i)))
                {
                    action = fileActions.get(i++);
                    %><td><%=showCheckboxes ? action.getDisplay() : action.getDisplay(iForm)%></td><%
                }
                %></tr></table></td><%
            }
            %><td width="100%">&nbsp;</td></tr><%
        }
        if (!showCheckboxes) {%></form><%}
    }
    if (row == 0)
    {
        %><tr><td><i>no files</i></td></tr><%
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
        %><a href="<%=actionToggle.getLocalURIString()%>"><%=PageFlowUtil.buttonImg("Process and Import Files")%></a>&nbsp;<%
    }
    else if (pipeRoot.getACL().hasPermission(context.getUser(), ACL.PERM_READ))
    {
        %><a href="<%=actionToggle.getLocalURIString()%>"><%=PageFlowUtil.buttonImg("Browse All Files")%></a>&nbsp;<%
    }

    if (null != StringUtils.trimToNull(AppProps.getInstance().getPipelineFTPHost()) && pipeRoot.getACL().hasPermission(context.getUser(), ACL.PERM_INSERT))
    {
        ActionURL dropUrl = (new ActionURL("ftp","drop",context.getContainer())).addParameter("pipeline",StringUtils.defaultString(form.getPath(),"/"));
        %><a href="<%=h(dropUrl.getLocalURIString())%>" target=_blank><%=PageFlowUtil.buttonImg("Upload files (ftp)")%></a>&nbsp;<%
    }
%><a href="returnToReferer.view"><%=PageFlowUtil.buttonImg("Cancel")%></a>

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
        form.appendChild(param)
    }
    form.action = parts[0];
    return true;
}
</script>

<%!
String f(String filename)
{
    String h = h(filename);
    return h.replace(" ", "&nbsp;");
}
%>