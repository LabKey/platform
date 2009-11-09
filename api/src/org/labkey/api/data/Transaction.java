/*
 * Copyright (c) 2009 LabKey Corporation
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

package org.labkey.api.data;

import org.labkey.api.collections.TTLCacheMap;

import java.sql.Connection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/*
* User: adam
* Date: Oct 28, 2009
* Time: 5:25:09 PM
*/

// Represents a single database transaction.  Holds onto the Connection, the temporary caches to use during that
// transaction, and the tasks to run immediately after commit to update the shared cache with removals.
class Transaction
{
    private final Connection _conn;
    private final Map<Object, TTLCacheMap> _caches = new HashMap<Object, TTLCacheMap>(20);
    private final LinkedList<Runnable> _commitTasks = new LinkedList<Runnable>();

    Transaction(Connection conn)
    {
        _conn = conn;
    }

    Connection getConnection()
    {
        return _conn;
    }

    Map<Object, TTLCacheMap> getCaches()
    {
        return _caches;
    }

    void addCommitTask(Runnable task)
    {
        _commitTasks.add(task);
    }

    void clearCommitTasks()
    {
        _commitTasks.clear();
    }

    void runCommitTasks()
    {
        while (!_commitTasks.isEmpty())
            _commitTasks.removeFirst().run();
    }
}
