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
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.security.xml.GroupType;
import org.labkey.security.xml.GroupsType;

import java.util.Collection;
import java.util.Collections;

/**
 * Created by susanh on 4/6/15.
 */
public class SecurityGroupImporterFactory extends AbstractFolderImportFactory
{
    @Override
    public FolderImporter create()
    {
        return new SecurityGroupImporter();
    }

    @Override
    public int getPriority()
    {
        return 1;
    }

    public class SecurityGroupImporter implements FolderImporter<FolderDocument.Folder>
    {
        @Override
        public String getDataType()
        {
            return FolderArchiveDataTypes.SECURITY_GROUPS;
        }

        @Override
        public String getDescription()
        {
            return "project-level groups";
        }


        @Override
        public void process(@Nullable PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            if (!isValidForImportArchive(ctx))
                return;

            if (!ctx.getContainer().isProject())
            {
                ctx.getLogger().warn("Import of groups outside of project context not supported.");
                return;
            }

            GroupsType groups = ctx.getXml().getGroups();

            // first create all the groups in the list
            for (GroupType xmlGroupType : groups.getGroupArray())
            {
                Integer groupId = SecurityManager.getGroupId(ctx.getContainer(), xmlGroupType.getName(), false);
                if (groupId == null)
                    SecurityManager.createGroup(ctx.getContainer(), xmlGroupType.getName());
            }
            // now populate the groups with their members
            for (GroupType xmlGroupType : groups.getGroupArray())
            {
                GroupManager.importGroupMembers(GroupManager.getGroup(ctx.getContainer(), xmlGroupType.getName(), xmlGroupType.getType()), xmlGroupType, ctx.getLogger(), ctx.getContainer());
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
            return ctx.getXml() != null && ctx.getXml().getGroups() != null;
        }
    }
}
