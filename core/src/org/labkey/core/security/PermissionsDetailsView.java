/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.core.security;

import org.labkey.api.view.WebPartView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;

import javax.servlet.ServletException;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.List;

/**
 * User: jeckels
* Date: May 2, 2008
*/
public class PermissionsDetailsView extends WebPartView
{
    String optionsAll =
            "<option value=" + org.labkey.api.security.SecurityManager.PermissionSet.ADMIN.getPermissions() + ">" + SecurityManager.PermissionSet.ADMIN.getLabel() + "</option>";
    String options =
            "<option value=" + SecurityManager.PermissionSet.EDITOR.getPermissions() + ">" + SecurityManager.PermissionSet.EDITOR.getLabel() + "</option>" +
                    "<option value=" + SecurityManager.PermissionSet.AUTHOR.getPermissions() + ">" + SecurityManager.PermissionSet.AUTHOR.getLabel() + "</option>" +
                    "<option value=" + SecurityManager.PermissionSet.READER.getPermissions() + ">" + SecurityManager.PermissionSet.READER.getLabel() + "</option>" +
                    "<option value=" + SecurityManager.PermissionSet.RESTRICTED_READER.getPermissions() + ">" + SecurityManager.PermissionSet.RESTRICTED_READER.getLabel() + "</option>" +
                    "<option value=" + SecurityManager.PermissionSet.SUBMITTER.getPermissions() + ">" + SecurityManager.PermissionSet.SUBMITTER.getLabel() + "</option>" +
                    "<option value=" + SecurityManager.PermissionSet.NO_PERMISSIONS.getPermissions() + ">" + SecurityManager.PermissionSet.NO_PERMISSIONS.getLabel() + "</option>";

    String helpText = "<b>Admin:</b> Users have all permissions on a folder.<br><br>" +
            "<b>Editor:</b> Users can modify data, but cannot perform administrative actions.<br><br>" +
            "<b>Author:</b> Users can modify their own data, but can only read others' data.<br><br>" +
            "<b>Reader:</b> Users can read text and data, but cannot modify it.<br><br>" +
            "<b>Submitter:</b> Users can insert new records, but cannot view or change other records.<br><br>" +
            "<b>No Permissions:</b> Users cannot view or modify any information in a folder.<br><br>" +
            "See the LabKey Server <a target=\"_new\" href=\"" + (new HelpTopic("configuringPerms", HelpTopic.Area.SERVER)).getHelpTopicLink() + "\">security</a> help topics for more information.";

    Container _c;
    Container _project;


    PermissionsDetailsView(Container c, String view)
    {
        _c = c;

        // get project for this container
        String path = _c.getPath();
        int i = path.indexOf('/', 1);
        if (i == -1) i = path.length();
        String project = path.substring(0, i);
        _project = ContainerManager.getForPath(project);

        addObject("view", view);

        if(c.isRoot())
            setTitle("Default permissions for new projects");
        else
            setTitle("Permissions for " + _c.getPath());
        setBodyClass("normal");
    }

    private void renderGroupTableRow(Group group, ACL acl, PrintWriter out, String displayName)
    {
        int id = group.getUserId();
        int perm = acl.getPermissions(id);
        if (perm == -1) perm = ACL.PERM_ALLOWALL; // HACK
        String htmlId = "group." + Integer.toHexString(id);
        out.print("<tr><td class=ms-searchform>");
        out.print(displayName);
        out.print("</td><td><select onchange=\"document.updatePermissions.inheritPermissions.checked=false;\" id=");
        out.print(htmlId);
        out.print(" name=");
        out.print(htmlId);
        out.print(">");
        if (!group.isGuests() || perm == ACL.PERM_ALLOWALL)
            out.print(optionsAll);
        out.print(options);
        SecurityManager.PermissionSet permSet = SecurityManager.PermissionSet.findPermissionSet(perm);
        if (permSet == null)
            out.print("<option value=" + perm + ">" + perm + "</option>");
        out.print("</select>");
        out.print("</td>");
        out.print("<td>");
        out.print(PageFlowUtil.helpPopup("LabKey Server Security Roles", helpText, true));
        out.print("</td>");

        if (!_c.isRoot())
        {
            out.print("<td class=\"normal\">");
            out.print("&nbsp;[<a href=\"" + ActionURL.toPathString("Security", "groupPermission", _c.getPath()) + "?group=" + group.getUserId() + "\">");
            out.print("permissions</a>]</td>");
        }
        out.print("</tr>");
        out.print("<script><!--\n");
        out.print("document.getElementById('");
        out.print(htmlId);
        out.print("').value = '" + perm + "';\n");
        out.print("--></script>\n");
    }


    @Override
    public void renderView(Object model, PrintWriter out) throws IOException, ServletException
    {
        boolean inherited = false;
        ACL acl = SecurityManager.getACL(_c, _c.getId());
        if (acl.isEmpty())
        {
            acl = _c.getAcl();
            inherited = true;
        }

        if (SecurityManager.isAdminOnlyPermissions(_c))
        {
            out.println("<b>Note: </b> Only administrators currently have access to this " + (_c.isProject() ? "project" : "") + " folder. <br>");
            if (_c.hasChildren())
            {
                Container[] children = ContainerManager.getAllChildren(_c);
                boolean childrenAdminOnly = true;
                for (Container child : children)
                {
                    if (!SecurityManager.isAdminOnlyPermissions(child))
                    {
                        childrenAdminOnly = false;
                        break;
                    }
                }
                out.println((childrenAdminOnly ? "No" : "Some") + " child folders can be accessed by non-administrators.");
            }
        }
        Group[] groups = SecurityManager.getGroups(_project, true);

        // browse link
        //out.println("Go back to <a href=\"" + ActionURL.toPathString("Project", "begin", _c.getPath()) + "\">" + _c.getPath() + "</a>");

        out.println("<form name=\"updatePermissions\" action=\"updatePermissions.post\" method=\"POST\">");

        if (!_c.isRoot())
        {
            if (!_c.isProject())
                out.println("<input type=checkbox name=inheritPermissions " + (inherited ? "checked" : "") + "> inherit permissions from " + _c.getParent().getPath());
            else
                out.println("<input type=hidden name=inheritPermissions value=off>");
        }

        out.println("<table>");
        // the first pass through will output only the project groups:
        Group guestsGroup = null;
        Group usersGroup = null;
        for (Group group : groups)
        {
            if (group.isGuests())
                guestsGroup = group;
            else if (group.isUsers())
                usersGroup = group;
            else if (group.isProjectGroup())
                renderGroupTableRow(group, acl, out, group.getName());
            else
            {
                // for groups that we don't want to display, we still have to output a hidden input
                // for the ACL value; otherwise, a submit with 'inherit' turned off will result in the
                // hidden groups having all permissions set to no-access.
                int id = group.getUserId();
                int perm = acl.getPermissions(id);
                if (perm == -1) perm = ACL.PERM_ALLOWALL; // HACK
                String htmlId = "group." + Integer.toHexString(id);
                out.println("<input type=\"hidden\" name=\"" + htmlId + "\" value=\"" + perm + "\">");
            }
        }
        if (usersGroup != null)
            renderGroupTableRow(usersGroup, acl, out, "All site users");
        if (guestsGroup != null)
            renderGroupTableRow(guestsGroup, acl, out, "Guests");
        out.println("</table>");
        out.println("<input type=\"image\" src=\"" + PageFlowUtil.buttonSrc("Update") + "\">");
        out.println("<input name=objectId type=hidden value=\"" + _c.getId() + "\">");
        out.println("<input name=view type=hidden value=\"" + getViewContext().get("view") + "\">");
        out.println("</form><br>");

        // Now render all the module-specific views registered for this page
        VBox vbox = new VBox();
        List<SecurityManager.ViewFactory> factories = SecurityManager.getViewFactories();

        for (SecurityManager.ViewFactory factory : factories)
            vbox.addView(factory.createView(getViewContext()));

        try
        {
            ViewContext ctx = getViewContext();
            vbox.render(ctx.getRequest(), ctx.getResponse());
        }
        catch(Exception e)
        {
            throw new ServletException(e);
        }
    }
}
