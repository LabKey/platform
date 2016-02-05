/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.api.util;

import java.util.Set;

/**
 * User: adam
 * Date: 4/13/12
 */
public interface MemTrackerListener
{
    /**
     * Called before GC and tallying of held objects. Implementors should purge held objects and (optionally) add
     * objects to the passed in set that should be ignored .
     */
    void beforeReport(Set<Object> set);
}
