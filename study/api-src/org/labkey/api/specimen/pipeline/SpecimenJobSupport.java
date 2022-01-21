/*
 * Copyright (c) 2009-2018 LabKey Corporation
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

package org.labkey.api.specimen.pipeline;

import org.labkey.api.admin.ImportException;
import org.labkey.api.study.importer.SimpleStudyImportContext;

import java.io.File;
import java.nio.file.Path;

/*
* User: adam
* Date: Sep 1, 2009
* Time: 3:02:30 PM
*/
public interface SpecimenJobSupport
{
    Path getSpecimenArchivePath() throws ImportException;
    boolean isMerge();
    SimpleStudyImportContext getImportContext();
}
