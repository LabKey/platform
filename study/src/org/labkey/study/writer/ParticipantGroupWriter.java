/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
package org.labkey.study.writer;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.ParticipantCategoryImpl;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.ParticipantMapper;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.participantGroups.CategoryType;
import org.labkey.study.xml.participantGroups.GroupType;
import org.labkey.study.xml.participantGroups.ParticipantGroupsDocument;
import org.labkey.study.xml.participantGroups.ParticipantGroupsType;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: klum
 * Date: Aug 31, 2011
 * Time: 12:57:02 PM
 */
public class ParticipantGroupWriter implements InternalStudyWriter
{
    public static final String FILE_NAME = "participant_groups.xml";
    public static final String DATA_TYPE = "Participant Groups";

    private List<ParticipantGroup> _groupsToCopy = Collections.emptyList();

    @Override
    public String getDataType()
    {
        return StudyArchiveDataTypes.PARTICIPANT_GROUPS;
    }

    @Override
    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        serialize(ctx, vf);
    }

    public void setGroupsToCopy(List<ParticipantGroup> groupsToCopy)
    {
        _groupsToCopy = groupsToCopy;
    }

    /**
     * Serialize participant groups to an xml bean object
     */
    private void serialize(StudyExportContext ctx, VirtualFile vf) throws IOException
    {
        User user = ctx.getUser();
        Container container = ctx.getContainer();
        List<ParticipantCategoryImpl> categories = ParticipantGroupManager.getInstance().getParticipantCategories(container, user);
        Set<ParticipantCategoryImpl> categoriesToCopy = new HashSet<>();

        for (ParticipantGroup pg : _groupsToCopy)
        {
            ParticipantCategoryImpl pc = ParticipantGroupManager.getInstance().getParticipantCategory(container, user, pg.getCategoryId());
            if (pc != null)
                categoriesToCopy.add(pc);
        }

        if (!categories.isEmpty())
        {
            ParticipantGroupsDocument doc = ParticipantGroupsDocument.Factory.newInstance();
            ParticipantGroupsType groups = doc.addNewParticipantGroups();

            ParticipantMapper participantMapper = ctx.getParticipantMapper();

            for (ParticipantCategoryImpl category : categories)
            {
                // categoriesToCopy will be empty for a folder/study export
                // categoriesToCopy will contain a list of categories for creating an ancillary study                
                if ((categoriesToCopy.isEmpty() || categoriesToCopy.contains(category)) && category.isShared())
                {
                    CategoryType pc = groups.addNewParticipantCategory();

                    pc.setLabel(category.getLabel());
                    pc.setType(category.getType());
                    pc.setAutoUpdate(category.isAutoUpdate());

                    if (category.getQueryName() != null)
                        pc.setQueryName(category.getQueryName());
                    if (category.getSchemaName() != null)
                        pc.setSchemaName(category.getSchemaName());
                    if (category.getViewName() != null)
                        pc.setViewName(category.getViewName());

                    pc.setDatasetId(category.getDatasetId());
                    if (category.getGroupProperty() != null)
                        pc.setGroupProperty(category.getGroupProperty());

                    for (ParticipantGroup group : category.getGroups())
                    {
                        // _groupsToCopy will be empty for a folder/study export
                        // _groupsToCopy will contain a list of groups for creating an ancillary study
                        if (_groupsToCopy.isEmpty() || _groupsToCopy.contains(group))
                        {
                            // issue 21433: don't try to export an empty group
                            String [] participantIds = group.getParticipantIds();
                            if (participantIds != null && participantIds.length > 0)
                            {
                                GroupType pg = pc.addNewGroup();

                                pg.setLabel(group.getLabel());
                                pg.setCategoryLabel(group.getCategoryLabel());

                                if (participantMapper.isAlternateIds())
                                {
                                    for (int i = 0; i < participantIds.length; i += 1)
                                        participantIds[i] = participantMapper.getMappedParticipantId(participantIds[i]);
                                }
                                pg.setParticipantIdArray(participantIds);
                            }
                        }
                    }
                }
            }
            vf.saveXmlBean(FILE_NAME, doc);
        }
    }
}
