/*
 * Copyright (c) 2010-2014 LabKey Corporation
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

import java.util.concurrent.ConcurrentHashMap;

/*
* User: adam
* Date: Nov 30, 2010
* Time: 9:11:00 AM
*/

//
// A thread-safe hash set that uses lock striping just like ConcurrentHashMap... because it IS a ConcurrentHashMap. This
// is a drop-in replacement for Collections.synchronizedSet(new HashSet()) that provides much better performance and
// scalability, albeit with a couple caveats:
//
// - ConcurrentHashSet does not support null elements; use a marker object instead.
// - Synchronizing is not needed for iterating a ConcurrentHashSet; in fact, if replacing a synchronized set, all
//   previous synchronization on the set itself must be removed.
//
public class ConcurrentHashSet<E> extends SetFromMap<E>
{
    public ConcurrentHashSet()
    {
        super(new ConcurrentHashMap<E, Boolean>());
    }
}
