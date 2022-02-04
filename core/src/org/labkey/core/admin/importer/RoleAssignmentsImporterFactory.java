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
package org.labkey.core.admin.importer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.writer.VirtualFile;
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

    public static class RoleAssignmentsImporter implements FolderImporter
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
        public void process(@Nullable PipelineJob job, FolderImportContext ctx, VirtualFile root) throws Exception
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
                SecurityPolicyManager.importRoleAssignments(ctx, policy, assignments);
            }
        }

        @NotNull
        @Override
        public Collection<PipelineJobWarning> postProcess(FolderImportContext ctx, VirtualFile root)
        {
            return Collections.emptyList();
        }

        @Override
        public boolean isValidForImportArchive(FolderImportContext ctx) throws ImportException
        {
            return ctx.getXml() != null && ctx.getXml().getRoleAssignments() != null;
        }
    }
}
