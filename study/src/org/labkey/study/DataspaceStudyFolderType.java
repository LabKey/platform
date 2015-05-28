/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.study;


import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.util.Set;

public class DataspaceStudyFolderType extends StudyFolderType
{
    public static final String NAME = StudyService.DATASPACE_FOLDERTYPE_NAME;

    DataspaceStudyFolderType(StudyModule module, Set<Module> activeModules)
    {
        super(NAME,
                "Work with all shared studies within the project.",
                module, activeModules);
    }

    @Override
    public void configureContainer(Container container, User user)
    {
        super.configureContainer(container, user);
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (null == study)
        {
            study = new StudyImpl(container, container.getTitle());
            study.setTimepointType(TimepointType.VISIT);
            study.setSubjectColumnName("SubjectID");
            study.setSubjectNounPlural("Subjects");
            study.setSubjectNounSingular("Subject");
            // this should be a project, but make sure we don't set sharing if it's not
            if (container.isProject())
            {
                study.setShareDatasetDefinitions(Boolean.TRUE);
                // NOTE: consider setShareVisitDefincitons(Boolean.TRUE);
            }
            StudyManager.getInstance().createStudy(user, study);
        }
    }

    @Override
    public boolean isProjectOnlyType()
    {
        return true;
    }
}
