/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.core.admin.importer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.security.xml.GroupRefType;
import org.labkey.security.xml.GroupRefsType;
import org.labkey.security.xml.UserRefType;
import org.labkey.security.xml.UserRefsType;
import org.labkey.security.xml.roleAssignment.RoleAssignmentType;
import org.labkey.security.xml.roleAssignment.RoleAssignmentsType;

import java.util.Collection;
import java.util.Collections;

/**
 * Created by susanh on 4/7/15.
 */
public class RoleAssignmentsImporterFactory extends AbstractFolderImportFactory
{
    @Override
    public FolderImporter create()
    {
        return new RoleAssignmentsImporter();
    }


    // we need to make sure the roles are imported after the groups so this should be larger that the SecurityGroupImporterFactory
    @Override
    public int getPriority()
    {
        return 2;
    }

    public class RoleAssignmentsImporter implements FolderImporter<FolderDocument.Folder>
    {
        @Override
        public String getDataType()
        {
            return FolderArchiveDataTypes.ROLE_ASSIGNMENTS;
        }

        @Override
        public String getDescription()
        {
            return getDataType().toLowerCase();
        }

        @Override
        public void process(@Nullable PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            if (!isValidForImportArchive(ctx))
                return;

            RoleAssignmentsType assignments = ctx.getXml().getRoleAssignments();

            // we get assignments inherited by default if we do not explicitly set any such assignments
            if (assignments.getInherited())
            {
                SecurityManager.setInheritPermissions(ctx.getContainer());
            }
            else
            {
                MutableSecurityPolicy policy = new MutableSecurityPolicy(ctx.getContainer());
                for (RoleAssignmentType assignmentXml : assignments.getRoleAssignmentArray())
                {
                    Role role = RoleManager.getRole(assignmentXml.getRole().getName());
                    if (role != null)
                    {
                        GroupRefsType groups = assignmentXml.getGroups();
                        if (groups != null)
                        {
                            for (GroupRefType groupRef : groups.getGroupArray())
                            {
                                UserPrincipal principal = GroupManager.getGroup(ctx.getContainer(), groupRef.getName(), groupRef.getType());
                                if (principal == null)
                                {
                                    ctx.getLogger().warn("Non-existent group in role assignment for role " + assignmentXml.getRole().getName() + " will be ignored: " + groupRef.getName());
                                }
                                else
                                {
                                    policy.addRoleAssignment(principal, role);
                                }
                            }
                        }
                        UserRefsType users = assignmentXml.getUsers();
                        if (users != null)
                        {
                            for (UserRefType userRef : users.getUserArray())
                            {
                                try
                                {
                                    ValidEmail validEmail = new ValidEmail(userRef.getName());
                                    UserPrincipal principal = UserManager.getUser(validEmail);

                                    if (principal == null)
                                    {
                                        ctx.getLogger().warn("Non-existent user in role assignment for role " + assignmentXml.getRole() + " will be ignored: " + userRef.getName());
                                    }
                                    else
                                    {
                                        policy.addRoleAssignment(principal, role);
                                    }
                                }
                                catch (ValidEmail.InvalidEmailException e)
                                {
                                    ctx.getLogger().error("Invalid email in role assignment for role " + assignmentXml.getRole());
                                }
                            }
                        }
                    }
                    else
                    {
                        ctx.getLogger().warn("Invalid role name ignored: " + assignmentXml.getRole());
                    }
                    SecurityPolicyManager.savePolicy(policy);
                }
            }
        }


        @NotNull
        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            return Collections.emptyList();
        }

        @Override
        public boolean isValidForImportArchive(ImportContext<FolderDocument.Folder> ctx) throws ImportException
        {
            return ctx.getXml() != null && ctx.getXml().getRoleAssignments() != null;
        }
    }
}
