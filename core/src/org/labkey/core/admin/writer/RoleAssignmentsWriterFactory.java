/*
 * Copyright (c) 2015-2018 LabKey Corporation
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

import org.labkey.api.admin.AbstractFolderContext.ExportType;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.data.Container;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument.Folder;
import org.labkey.security.xml.roleAssignment.RoleAssignmentsType;

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

    public static class RoleAssignmentsWriter extends BaseFolderWriter
    {
        @Override
        public String getDataType()
        {
            return FolderArchiveDataTypes.ROLE_ASSIGNMENTS;
        }

        @Override
        public void write(Container c, FolderExportContext ctx, VirtualFile vf) throws Exception
        {
            SecurityPolicy existingPolicy = SecurityPolicyManager.getPolicy(c);

            if (!existingPolicy.getAssignments().isEmpty())
            {
                Folder folderXml = ctx.getXml();
                RoleAssignmentsType roleAssignments = folderXml.addNewRoleAssignments();
                if (c.isInheritedAcl())
                {
                    roleAssignments.setInherited(true);
                }
                else
                {
                    SecurityPolicyManager.exportRoleAssignments(existingPolicy, roleAssignments);
                }
            }
        }

        @Override
        public boolean selectedByDefault(ExportType type)
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
