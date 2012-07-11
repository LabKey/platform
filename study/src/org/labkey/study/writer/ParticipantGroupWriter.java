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
package org.labkey.study.writer;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.ParticipantCategoryImpl;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.participantGroups.CategoryType;
import org.labkey.study.xml.participantGroups.GroupType;
import org.labkey.study.xml.participantGroups.ParticipantGroupsDocument;
import org.labkey.study.xml.participantGroups.ParticipantGroupsType;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Aug 31, 2011
 * Time: 12:57:02 PM
 */
public class ParticipantGroupWriter implements InternalStudyWriter
{
    public static final String FILE_NAME = "participant_groups.xml";
    public static final String DATA_TYPE = "Participant Groups";

    private List<ParticipantCategoryImpl> _categoriesToCopy = Collections.emptyList();

    @Override
    public String getSelectionText()
    {
        return DATA_TYPE;
    }

    @Override
    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        serialize(ctx.getUser(), ctx.getContainer(), vf);
    }

    public void setCategoriesToCopy(List<ParticipantCategoryImpl> categoriesToCopy)
    {
        _categoriesToCopy = categoriesToCopy;
    }

    /**
     * Serialize participant groups to an xml bean object
     */
    private void serialize(User user, Container container, VirtualFile vf) throws IOException
    {
        ParticipantCategoryImpl[] categories = ParticipantGroupManager.getInstance().getParticipantCategories(container, user);

        if (categories.length > 0)
        {
            ParticipantGroupsDocument doc = ParticipantGroupsDocument.Factory.newInstance();
            ParticipantGroupsType groups = doc.addNewParticipantGroups();

            for (ParticipantCategoryImpl category : categories)
            {
                if (_categoriesToCopy.isEmpty() || _categoriesToCopy.contains(category))
                {
                    CategoryType pc = groups.addNewParticipantCategory();

                    pc.setLabel(category.getLabel());
                    pc.setType(category.getType());
                    pc.setShared(category.isShared());
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
                        GroupType pg = pc.addNewGroup();

                        pg.setLabel(group.getLabel());
                        pg.setCategoryLabel(group.getCategoryLabel());
                        pg.setParticipantIdArray(group.getParticipantIds());
                    }
                }
            }
            vf.saveXmlBean(FILE_NAME, doc);
        }
    }
}
