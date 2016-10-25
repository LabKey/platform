/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.core.admin.writer;

import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.security.Group;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.security.xml.GroupEnumType;
import org.labkey.security.xml.GroupRefType;
import org.labkey.security.xml.GroupRefsType;
import org.labkey.security.xml.UserRefType;
import org.labkey.security.xml.UserRefsType;
import org.labkey.security.xml.roleAssignment.RoleAssignmentType;
import org.labkey.security.xml.roleAssignment.RoleAssignmentsType;
import org.labkey.security.xml.roleAssignment.RoleRefType;

import java.util.List;
import java.util.Map;

/**
 * Created by susanh on 4/7/15.
 */
public class RoleAssignmentsWriterFactory implements FolderWriterFactory
{
    @Override
    public FolderWriter create()
    {
        return new RoleAssignmentsWriter();
    }

    public class RoleAssignmentsWriter extends BaseFolderWriter
    {
        @Override
        public String getDataType()
        {
            return FolderArchiveDataTypes.ROLE_ASSIGNMENTS;
        }

        @Override
        public void write(Container c, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
        {
            SecurityPolicy existingPolicy = SecurityPolicyManager.getPolicy(c);


            if (!existingPolicy.getAssignments().isEmpty())
            {
                FolderDocument.Folder folderXml = ctx.getXml();
                RoleAssignmentsType roleAssignments = folderXml.addNewRoleAssignments();
                if (c.isInheritedAcl())
                {
                    roleAssignments.setInherited(true);
                }
                else
                {
                    Map<String, Map<PrincipalType, List<UserPrincipal>>> map = existingPolicy.getAssignmentsAsMap();

                    for (String roleName : map.keySet())
                    {
                        Map<PrincipalType, List<UserPrincipal>> assignees = map.get(roleName);
                        RoleAssignmentType roleAssignment = roleAssignments.addNewRoleAssignment();
                        RoleRefType role = roleAssignment.addNewRole();
                        role.setName(roleName);
                        if (assignees.get(PrincipalType.GROUP) != null)
                        {
                            GroupRefsType groups = roleAssignment.addNewGroups();
                            for (UserPrincipal user : assignees.get(PrincipalType.GROUP))
                            {
                                Group group = (Group) user;
                                GroupRefType groupRef = groups.addNewGroup();
                                groupRef.setName(group.getName());
                                groupRef.setType(group.isProjectGroup() ? GroupEnumType.PROJECT : GroupEnumType.SITE);
                            }
                        }
                        if (assignees.get(PrincipalType.USER) != null)
                        {
                            UserRefsType users = roleAssignment.addNewUsers();
                            for (UserPrincipal user : assignees.get(PrincipalType.USER))
                            {
                                UserRefType userRef = users.addNewUser();
                                userRef.setName(user.getName());
                            }
                        }

                    }
                }
            }
        }

        @Override
        public boolean selectedByDefault(AbstractFolderContext.ExportType type)
        {
            return false;
        }

        @Override
        public boolean includeWithTemplate()
        {
            return false;
        }
    }
}
