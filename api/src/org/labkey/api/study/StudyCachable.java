/*
 * Copyright (c) 2011-2019 LabKey Corporation
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

import org.labkey.api.data.Container;

/**
 * An object owned by a study that's eligible to be cached.
 * User: brittp
 * Date: Feb 10, 2006
 */
public interface StudyCachable<T>
{
    /** Create a new, mutable copy of this object */
    T createMutable();
    /** Make this object immutable */
    void lock();
    Container getContainer();
    Object getPrimaryKey();
}
