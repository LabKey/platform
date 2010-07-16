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
package org.labkey.api.cache;

import java.util.concurrent.atomic.AtomicLong;

/**
* User: adam
* Date: Jun 22, 2010
* Time: 12:28:48 AM
*/
public class Stats
{
    public AtomicLong gets = new AtomicLong(0);
    public AtomicLong misses = new AtomicLong(0);
    public AtomicLong puts = new AtomicLong(0);
    public AtomicLong expirations = new AtomicLong(0);
    public AtomicLong removes = new AtomicLong(0);
    public AtomicLong clears = new AtomicLong(0);
    public AtomicLong max_size = new AtomicLong(0);
}
