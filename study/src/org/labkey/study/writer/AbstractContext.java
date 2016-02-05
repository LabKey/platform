/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.LoggerGetter;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.xml.StudyDocument;

import java.util.Set;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 10:00:46 AM
 */
public abstract class AbstractContext extends AbstractImportContext<StudyDocument.Study, StudyDocument>
{
    protected AbstractContext(User user, Container c, StudyDocument studyDoc, Set<String> dataTypes, LoggerGetter logger, @Nullable VirtualFile root)
    {
        super(user, c, studyDoc, dataTypes, logger, root);
    }

    // Study node -- interesting to any study writer that needs to set info into study.xml
    public StudyDocument.Study getXml() throws ImportException
    {
        return getDocument().getStudy();
    }

    @NotNull
    public Container getProject()
    {
        Container project = getContainer().getProject();
        if (null == project)
            throw new IllegalStateException("Study Import/Export must happen within a project.");
        return project;
    }

    public boolean isDataspaceProject()
    {
        Container project = getProject();
        return project.isDataspace();
    }
}
