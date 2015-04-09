package org.labkey.core.admin.writer;

import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.FolderWriterNames;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.User;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.api.security.SecurityManager;
import org.labkey.security.xml.GroupType;
import org.labkey.security.xml.GroupsType;

import java.util.ArrayList;

/**
 * Created by susanh on 4/6/15.
 */
public class SecurityGroupWriterFactory implements FolderWriterFactory
{
    @Override
    public FolderWriter create()
    {
        return new SecurityGroupWriter();
    }

    public class SecurityGroupWriter extends BaseFolderWriter
    {
        @Override
        public String getSelectionText()
        {
            return FolderWriterNames.SECURITY_GROUPS;
        }

        @Override
        public void write(Container c, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
        {
            FolderDocument.Folder folderXml = ctx.getXml();

            Group[] groups = SecurityManager.getGroups(c, false);

            if (groups.length != 0)
            {
                GroupsType xmlGroups = folderXml.addNewGroups();
                for (Group group : groups)
                {
                    GroupType xmlGroup = xmlGroups.addNewGroup();
                    GroupManager.exportGroupMembers(group, new ArrayList<Group>(SecurityManager.getGroupMembers(group, MemberType.GROUPS)), new ArrayList<User>(SecurityManager.getGroupMembers(group, MemberType.ACTIVE_USERS)), xmlGroup);
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

        @Override
        public boolean show(Container container)
        {
            return container.isProject();
        }
    }
}
