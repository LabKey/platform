/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.api.specimen.pipeline.SpecimenJobSupport;
import org.springframework.validation.BindException;

import java.nio.file.Path;

/*
* User: adam
* Date: Aug 31, 2009
* Time: 2:02:54 PM
*/
public interface StudyJobSupport extends SpecimenJobSupport
{
    StudyImpl getStudy();

    StudyImpl getStudy(boolean allowNullStudy);

    @Override
    StudyImportContext getImportContext();

    VirtualFile getRoot();

    String getOriginalFilename();

    @Deprecated
    BindException getSpringErrors();

    void downloadCloudArchive(Path studyXml, BindException errors);
}
