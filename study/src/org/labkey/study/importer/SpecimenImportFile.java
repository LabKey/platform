/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.labkey.api.reader.DataLoader;
import org.labkey.api.study.SpecimenImportStrategy;
import org.labkey.study.importer.SpecimenImporter.SpecimenTableType;

import java.io.IOException;

/*
* User: adam
* Date: Feb 16, 2013
* Time: 6:58:21 AM
*/
public interface SpecimenImportFile
{
    public SpecimenImportStrategy getStrategy();
    public SpecimenTableType getTableType();
    public DataLoader getDataLoader() throws IOException;
}
