/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
package org.labkey.core.user;

import org.apache.commons.collections4.MultiValuedMap;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerType;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.view.JspView;
import org.labkey.api.view.VBox;
import org.labkey.core.security.SecurityController.FolderAccessForm;
import org.labkey.core.user.UserController.AccessDetail;
import org.labkey.core.user.UserController.AccessDetailRow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Created by adam on 5/31/2017.
 */

// TODO: Reconcile with FolderAccessAction as well
// TODO: Move AccessDetail, FolderAccessForm, AccessDetail into this class
public class SecurityAccessView extends VBox
{
    private final User _currentUser;
    private final UserPrincipal _principal;
    private final boolean _showAll;

    private final MultiValuedMap<Container, Container> _containerTree;
    private final Map<Container, List<Group>> _projectGroupCache = new HashMap<>();
    private final List<AccessDetailRow> _rows = new ArrayList<>();
    private final Set<Container> _containersInList = new HashSet<>();
    private final Set<Role> _userSiteRoles = new TreeSet<>((o1, o2) -> o1.getDisplayName().compareTo(o2.getDisplayName()));
    private final Set<Role> _allSiteRoles = RoleManager.getSiteRoles();

    public SecurityAccessView(Container c, User currentUser, @NotNull UserPrincipal principal, boolean showAll)
    {
        _currentUser = currentUser;
        _principal = principal;
        _showAll = showAll;
        _containerTree = c.isRoot() ? ContainerManager.getContainerTree() : ContainerManager.getContainerTree(c.getProject());

        // Issue 37124 : filter out site level roles from the permission report
        boolean filterSiteRoles = c.isRoot();

        buildAccessDetailList(c.isRoot() ? ContainerManager.getRoot() : null, filterSiteRoles, 0);

        FolderAccessForm accessForm = new FolderAccessForm();
        accessForm.setShowAll(showAll);
        accessForm.setShowCaption("show all folders");
        accessForm.setHideCaption("hide unassigned folders");
        addView(new JspView<>("/org/labkey/core/user/toggleShowAll.jsp", accessForm));

        AccessDetail details = new AccessDetail(_rows);
        details.setActive(_principal.isActive());
        JspView<AccessDetail> accessView = new JspView<>("/org/labkey/core/user/securityAccess.jsp", details);
        accessView.setTitle("Folder Role Assignments");
        accessView.setFrame(FrameType.PORTAL);
        addView(accessView);

        if (filterSiteRoles && !_userSiteRoles.isEmpty())
        {
            // don't include the site roles in the permission report, just list them
            // at the bottom of the page
            JspView<?> siteRoles = new JspView<>("/org/labkey/core/user/siteRoles.jsp", _userSiteRoles);
            siteRoles.setTitle("Site-Level Role Assignments");
            addView(siteRoles);
        }
    }

    private void buildAccessDetailList(Container parent, boolean filterSiteRoles, int depth)
    {
        // Note: _containerTree.get() returns empty collection if no mapping or no children
        for (Container child : _containerTree.get(parent))
        {
            // Skip containers that don't have directly assigned permissions (they inherit from their parent)
            if (!child.isContainerFor(ContainerType.DataType.permissions))
                continue;

            SecurityPolicy policy = child.getPolicy();
            Collection<Role> allRoles = policy.getEffectiveRoles(_principal);
            allRoles.remove(RoleManager.getRole(NoPermissionsRole.class)); //ignore no perms

            List<Role> roles = new ArrayList<>();
            if (filterSiteRoles)
            {
                // filter out site level roles to avoid cluttering the permissions report, this is the case
                // only when viewing the permissions report at the site level
                for (Role role : allRoles)
                {
                    if (_allSiteRoles.contains(role))
                        _userSiteRoles.add(role);
                    else
                        roles.add(role);
                }
            }
            else
                roles.addAll(allRoles);

            Map<String, List<Group>> childAccessGroups = new TreeMap<>();

            for (Role role : roles)
            {
                childAccessGroups.put(role.getName(), new ArrayList<>());
            }

            if (roles.size() > 0)
            {
                Container project = child.getProject();
                List<Group> groups = _projectGroupCache.computeIfAbsent(project, k -> SecurityManager.getGroups(project, true));
                for (Group group : groups)
                {
                    if (_principal.isInGroup(group.getUserId()))
                    {
                        Collection<Role> groupRoles = policy.getAssignedRoles(group);
                        for (Role role : roles)
                        {
                            if (groupRoles.contains(role))
                                childAccessGroups.get(role.getName()).add(group);
                        }
                    }
                }
            }

            if (_showAll || roles.size() > 0)
            {
                int index = _rows.size();
                _rows.add(new AccessDetailRow(_currentUser, child, _principal, childAccessGroups, depth));
                _containersInList.add(child);

                //Ensure parents of any accessible folder are in the tree. If not add them with no access info
                int newDepth = depth;
                while (parent != null && !parent.isRoot() && !_containersInList.contains(parent))
                {
                    _rows.add(index, new AccessDetailRow(_currentUser, parent, _principal, Collections.emptyMap(), --newDepth));
                    _containersInList.add(parent);
                    parent = parent.getParent();
                }
            }

            buildAccessDetailList(child, filterSiteRoles, depth + 1);
        }
    }
}
