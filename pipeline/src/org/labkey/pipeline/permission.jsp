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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.security.Group" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.common.util.Pair" %>
<%@ page import="org.labkey.pipeline.PipelineController" %>
<%@ page import="java.io.IOException" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<div style="width:240px;">
<%
    PipelineController.PermissionView me = (PipelineController.PermissionView)HttpView.currentView();
    ViewContext context = me.getViewContext();
    ACL acl = me.getModelBean();
    if (null == acl)
        acl = new ACL();
    Container c = context.getContainer();

    boolean enableFTP = !acl.isEmpty();
%>
<b>Pipeline&nbsp;Files&nbsp;Permissions</b><br>
These permissions control whether pipeline files can be downloaded and updated via the web server (broswer, WebDAV), or the
Labkey FTP server if it is configured.
<p />
<form id="permissionsForm" action="updateRootPermissions.post" method="POST">
<input id="enabledCheckbox" type="checkbox" name="enable" <%=enableFTP?"checked":""%> onclick="toggleEnableFTP(this)" onchange="toggleEnableFTP(this)"> share files via web site or FTP server<br>
    <%
    Group[] groups = org.labkey.api.security.SecurityManager.getGroups(c.getProject(), true);
    Pair[] optionsFull = new Pair[]
    {
        new Pair<String,Integer>("no ftp access",0),
        new Pair<String,Integer>("read files",ACL.PERM_READ),
        new Pair<String,Integer>("create files",ACL.PERM_READ|ACL.PERM_INSERT),
        new Pair<String,Integer>("create and delete",ACL.PERM_READ|ACL.PERM_INSERT|ACL.PERM_UPDATE|ACL.PERM_DELETE)
    };
    Pair[] optionsGuest = new Pair[] {optionsFull[0],optionsFull[1]};

    int i=0;
    %><b class="labkey-message">Global groups</b><table><%  // FIELDSET is broken on firefox  <fieldset><legend>Global groups</legend>
    for (Group g : groups)
    {
        if (g.isProjectGroup())
            continue;
        int perm = acl.getPermissions(g.getUserId());
        String name = h(g.getName());
        if (g.isAdministrators())
            name = "Site&nbsp;Administrators";
        else if (g.isUsers())
            name = "All Users";
        %><tr><td><%=name%><input type="hidden" name="groups[<%=i%>]" value="<%=g.getUserId()%>"></td><td><select name="perms[<%=i%>]">
        <%=writeOptions(g.isGuests() ? optionsGuest : optionsFull, perm)%>
        </select></td></tr><%
        i++;
    }
    %></table><b class="labkey-message">Project groups</b><table><%
    for (Group g : groups)
    {
        if (!g.isProjectGroup())
            continue;
        int perm = acl.getPermissions(g.getUserId());
        %><tr><td><%=h(g.getName())%><input type="hidden" name="groups[<%=i%>]" value="<%=g.getUserId()%>"></td><td><select name="perms[<%=i%>]">
        <%=writeOptions(g.isGuests() ? optionsGuest : optionsFull, perm)%>
        </select></td></tr><%
        i++;
    }
    %></table><br><%
%>
<input type="image" src="<%=PageFlowUtil.submitSrc()%>">
</form>
<script type="text/javascript">
toggleEnableFTP(document.getElementById("enabledCheckbox"));
        
function toggleEnableFTP(checkbox)
{
    var i;
    var checked = checkbox.checked;
//    var sets = document.getElementsByTagName("FIELDSET");
//    for (i=0 ; i<sets.length ; i++)
//        sets[i].style.backgroundColor = checked ? "#ffffff" : "#eeeeee";
    var form = document.getElementById("permissionsForm");
    var elements = form.getElementsByTagName("select"); 
    for (i in elements)
    {
        var e = elements[i];
        e.disabled = !checked;
    }
}
</script>


<%!
    String writeOptions(Pair[] options, int value) throws IOException
    {
        StringBuffer out = new StringBuffer();
        boolean selected = false;
        for (Pair option : options)
        {
            out.append("<option value=\"");
            out.append(h(option.getValue()));
            if (option.getValue().equals(value))
            {
                selected = true;
                out.append("\" selected>");
            }
            else
                out.append("\">");
            out.append(h(option.getKey()));
            out.append("</option>");
        }
        if (!selected)
        {
            out.append("<option value=\"");
            out.append(""+value);
                out.append("\" selected>");
            out.append(""+value);
            out.append("</option>");
        }
        return out.toString();
    }
%>
</div>