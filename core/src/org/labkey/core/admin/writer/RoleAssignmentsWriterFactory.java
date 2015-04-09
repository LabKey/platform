package org.labkey.core.admin.writer;

import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.FolderWriterNames;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
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
        public String getSelectionText()
        {
            return FolderWriterNames.ROLE_ASSIGNMENTS;
        }

        @Override
        public void write(Container c, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
        {
            SecurityPolicy existingPolicy = SecurityPolicyManager.getPolicy(c);


            if (!existingPolicy.getAssignments().isEmpty())
            {
                FolderDocument.Folder folderXml = ctx.getXml();
                RoleAssignmentsType roleAssignments = folderXml.addNewRoleAssignments();
                roleAssignments.setInherited(c.isInheritedAcl());

                if (!c.isInheritedAcl())
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
                            for (UserPrincipal group : assignees.get(PrincipalType.GROUP))
                            {
                                GroupRefType groupRef = groups.addNewGroup();
                                groupRef.setName(group.getName());
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
        public boolean supportsVirtualFile()
        {
            return true;
        }

        @Override
        public boolean selectedByDefault(AbstractFolderContext.ExportType type)
        {
            return false;
        }
    }
}
