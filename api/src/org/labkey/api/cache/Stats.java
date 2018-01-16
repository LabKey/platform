/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
 * Simple bean for tracking cache usage and efficacy
 * User: adam
 * Date: Jun 22, 2010
 */
public class Stats
{
    public final AtomicLong gets = new AtomicLong(0);
    public final AtomicLong misses = new AtomicLong(0);
    public final AtomicLong puts = new AtomicLong(0);
    public final AtomicLong expirations = new AtomicLong(0);
    public final AtomicLong removes = new AtomicLong(0);
    public final AtomicLong clears = new AtomicLong(0);
    public final AtomicLong max_size = new AtomicLong(0);
}
