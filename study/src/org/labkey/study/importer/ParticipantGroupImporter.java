/*
 * Copyright (c) 2011 LabKey Corporation
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
import org.labkey.api.data.DbScope;
import org.labkey.api.study.InvalidFileException;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.model.ParticipantCategory;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.writer.ParticipantGroupWriter;
import org.labkey.study.xml.participantGroups.CategoryType;
import org.labkey.study.xml.participantGroups.GroupType;
import org.labkey.study.xml.participantGroups.ParticipantGroupsDocument;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
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
    public void process(StudyImpl study, ImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
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

    public void process(StudyImpl study, ImportContext ctx, XmlObject xmlObject) throws Exception
    {
        if (xmlObject instanceof ParticipantGroupsDocument)
        {
            DbScope scope = StudySchema.getInstance().getSchema().getScope();

            try
            {
                ParticipantGroupsDocument doc = (ParticipantGroupsDocument)xmlObject;
                XmlBeansUtil.validateXmlDocument(doc);

                scope.ensureTransaction();

                Map<String, ParticipantCategory> existingGroups = new HashMap<String, ParticipantCategory>();
                for (ParticipantCategory group : ParticipantGroupManager.getInstance().getParticipantCategories(ctx.getContainer(), ctx.getUser()))
                    existingGroups.put(group.getLabel(), group);

                // create the imported participant groups
                for (CategoryType category : doc.getParticipantGroups().getParticipantCategoryArray())
                {
                    // overwrite any existing groups of the same name
                    if (existingGroups.containsKey(category.getLabel()))
                        ParticipantGroupManager.getInstance().deleteParticipantCategory(ctx.getContainer(), ctx.getUser(), existingGroups.get(category.getLabel()));
                    
                    ParticipantCategory pc = new ParticipantCategory();

                    pc.setContainer(ctx.getContainer().getId());
                    pc.setLabel(category.getLabel());
                    pc.setType(category.getType());
                    pc.setShared(category.getShared());
                    pc.setAutoUpdate(category.getAutoUpdate());

                    pc.setSchemaName(category.getSchemaName());
                    pc.setQueryName(category.getQueryName());
                    pc.setViewName(category.getViewName());

                    pc.setDatasetId(category.getDatasetId());
                    pc.setGroupProperty(category.getGroupProperty());

                    List<String> participants = new ArrayList<String>();
                    for (GroupType group : category.getGroupArray())
                    {
                        participants.addAll(Arrays.asList(group.getParticipantIdArray()));
                    }
                    ParticipantGroupManager.getInstance().setParticipantCategory(ctx.getContainer(), ctx.getUser(), pc,
                            participants.toArray(new String[participants.size()]));
                }
                scope.commitTransaction();
            }
            finally
            {
                scope.closeConnection();
            }
        }
    }
}
