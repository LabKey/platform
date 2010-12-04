/*
 * Copyright (c) 2010 LabKey Corporation
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

package org.labkey.api.collections;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/*
* User: adam
* Date: Nov 30, 2010
* Time: 8:24:16 AM
*/

// As the name suggests, this is a concurrent, case-insensitive set of strings that is sorted ignoring case. A
// ConcurrentSkipListSet is not as efficient as a concurrent case-insensitive hash set would be (if we had one)
// but it should beat a synchronized CaseInsensitiveHashSet.
public class ConcurrentCaseInsensitiveSortedSet extends ConcurrentSkipListSet<String>
{
    public ConcurrentCaseInsensitiveSortedSet()
    {
        super(String.CASE_INSENSITIVE_ORDER);
    }

    public ConcurrentCaseInsensitiveSortedSet(Set<String> set)
    {
        this();
        addAll(set);
    }
}
