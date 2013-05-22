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

package org.labkey.api.study;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.writer.VirtualFile;

/*
* User: adam
* Date: Feb 13, 2013
* Time: 2:09:06 PM
*/
public interface SpecimenImportStrategyFactory
{
    // Returns a SpecimenImportStrategy if this factory claims the current file, otherwise returns null
    @Nullable
    SpecimenImportStrategy get(DbSchema schema, Container c, VirtualFile dir, String fileName);    // TODO: Other context! Logger?
}
