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

import org.labkey.api.study.writer.BaseStudyWriter;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;

import java.util.Set;

/*
* User: adam
* Date: Aug 26, 2009
* Time: 1:53:21 PM
*/
public interface InternalStudyWriter extends BaseStudyWriter<StudyImpl, StudyExportContext>
{
    @Override
    default boolean includeWithTemplate()
    {
        return true;
    }

    default void write1(StudyImpl study, StudyExportContext ctx, VirtualFile root, Set<String> dataTypes) throws Exception
    {
    }
}
