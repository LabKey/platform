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

import org.labkey.api.data.Filter;
import java.io.Closeable;
import java.util.Map;

/*
* User: adam
* Date: Feb 13, 2013
* Time: 2:02:57 PM
*/
public interface SpecimenImportStrategy extends Closeable
{
    org.labkey.api.util.Filter<Map<String, Object>> getImportFilter();
    Filter getDeleteFilter();
    Filter getInsertFilter();
}