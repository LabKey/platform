/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.labkey.api.admin.ImportException;
import org.labkey.api.writer.VirtualFile;
import org.springframework.validation.BindException;

/*
* User: adam
* Date: Aug 31, 2009
* Time: 2:06:34 PM
*/
public interface InternalStudyImporter
{
    String getDataType();
    String getDescription();
    void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception;

    /**
     * Validate if the study importer is valid for the given import context. Default to true.
     * @return boolean
     */
    default boolean isValidForImportArchive(StudyImportContext ctx, VirtualFile root) throws ImportException
    {
        return true;
    }
}
