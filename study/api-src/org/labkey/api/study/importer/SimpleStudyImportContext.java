/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.api.study.importer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.LoggerGetter;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.writer.AbstractStudyContext;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.xml.StudyDocument;

import java.util.Set;

public class SimpleStudyImportContext extends AbstractStudyContext
{
    public SimpleStudyImportContext(User user, Container c, StudyDocument studyDoc, Set<String> dataTypes, LoggerGetter logger, @Nullable VirtualFile root)
    {
        super(user, c, studyDoc, dataTypes, logger, root);
    }

    public Study getStudy()
    {
        StudyService ss = StudyService.get();
        assert null != ss;

        return ss.getStudy(getContainer());
    }
}
