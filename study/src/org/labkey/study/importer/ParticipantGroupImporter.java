/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.study.importer;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.data.DbScope;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.model.ParticipantCategoryImpl;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.writer.ParticipantGroupWriter;
import org.labkey.study.writer.StudyArchiveDataTypes;
import org.labkey.study.xml.participantGroups.CategoryType;
import org.labkey.study.xml.participantGroups.GroupType;
import org.labkey.study.xml.participantGroups.ParticipantGroupsDocument;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: Aug 31, 2011
 * Time: 12:57:55 PM
 */
public class ParticipantGroupImporter implements InternalStudyImporter
{
    @Override
    public String getDescription()
    {
        return "participant groups";
    }

    @Override
    public String getDataType() { return StudyArchiveDataTypes.PARTICIPANT_GROUPS; }

    @Override
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
        if (!ctx.isDataTypeSelected(getDataType()))
            return;

        if (!isValidForImportArchive(ctx, root))
            return;

        StudyImpl study = ctx.getStudy();
        try
        {
            XmlObject xml = root.getXmlBean(ParticipantGroupWriter.FILE_NAME);
            if (xml instanceof ParticipantGroupsDocument)
            {
                xml.validate(XmlBeansUtil.getDefaultParseOptions());
                process(study, ctx, xml);
            }
        }
        catch (XmlException x)
        {
            throw new InvalidFileException(root.getRelativePath(ParticipantGroupWriter.FILE_NAME), x);
        }
    }

    @Override
    public boolean isValidForImportArchive(StudyImportContext ctx, VirtualFile root) throws ImportException
    {
        try
        {
            return root != null && root.getXmlBean(ParticipantGroupWriter.FILE_NAME) != null;
        }
        catch (IOException e)
        {
            return false;
        }
    }

    public void process(StudyImpl study, StudyImportContext ctx, XmlObject xmlObject) throws Exception
    {
        if (xmlObject instanceof ParticipantGroupsDocument)
        {
            ctx.getLogger().info("Loading " + getDescription());

            DbScope scope = StudySchema.getInstance().getSchema().getScope();

            ParticipantGroupsDocument doc = (ParticipantGroupsDocument)xmlObject;
            XmlBeansUtil.validateXmlDocument(doc);

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                Map<String, ParticipantCategoryImpl> existingGroups = new HashMap<>();
                for (ParticipantCategoryImpl group : ParticipantGroupManager.getInstance().getParticipantCategories(ctx.getContainer(), ctx.getUser()))
                    existingGroups.put(group.getLabel(), group);

                Map<String, String> existingParticipants = new HashMap<>();
                for (String id : StudyManager.getInstance().getParticipantIds(study,ctx.getUser()))
                    existingParticipants.put(id, id);

                // create the imported participant groups
                for (CategoryType category : doc.getParticipantGroups().getParticipantCategoryArray())
                {
                    // overwrite any existing groups of the same name
                    if (existingGroups.containsKey(category.getLabel()))
                        ParticipantGroupManager.getInstance().deleteParticipantCategory(ctx.getContainer(), ctx.getUser(), existingGroups.get(category.getLabel()));
                    
                    ParticipantCategoryImpl pc = new ParticipantCategoryImpl();

                    pc.setContainer(ctx.getContainer().getId());
                    pc.setLabel(category.getLabel());
                    pc.setType(category.getType());

                    if (category.getOwnerId() != null)
                        pc.setOwnerId(category.getOwnerId().intValue());

                    pc.setAutoUpdate(category.getAutoUpdate());

                    pc.setSchemaName(category.getSchemaName());
                    pc.setQueryName(category.getQueryName());
                    pc.setViewName(category.getViewName());

                    pc.setDatasetId(category.getDatasetId());
                    pc.setGroupProperty(category.getGroupProperty());

                    pc = ParticipantGroupManager.getInstance().setParticipantCategory(ctx.getContainer(), ctx.getUser(), pc);

                    for (GroupType group : category.getGroupArray())
                    {
                        ParticipantGroup pg = new ParticipantGroup();

                        pg.setCategoryId(pc.getRowId());
                        pg.setContainerId(ctx.getContainer().getId());
                        pg.setLabel(group.getLabel());
                        pg.setCategoryLabel(pc.getLabel());

                        List<String> ids = new ArrayList<>();
                        for (String id : group.getParticipantIdArray())
                        {
                            if (existingParticipants.containsKey(id))
                                ids.add(id);
                        }

                        if (!ids.isEmpty())
                        {
                            pg.setParticipantIds(ids.toArray(new String[ids.size()]));
                            ParticipantGroupManager.getInstance().setParticipantGroup(ctx.getContainer(), ctx.getUser(), pg);
                        }
                        else
                        {
                            ctx.getLogger().info("Skipping participant group " + group.getLabel() + " since none of its members could be found");
                        }
                    }
                }
                transaction.commit();
                ParticipantGroupManager.getInstance().clearCache(ctx.getContainer());
            }

            ctx.getLogger().info("Done importing " + getDescription());
        }
    }
}
