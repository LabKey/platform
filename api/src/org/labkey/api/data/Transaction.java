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
import java.util.Map;

/*
* User: adam
* Date: Oct 28, 2009
* Time: 5:25:09 PM
*/

// Represents a single database transaction.  Holds onto the Connection and the temporary caches to use during that transaction.
public class Transaction
{
    private final Connection _conn;
    private final Map<Object, TTLCacheMap> _transactionCaches = new HashMap<Object, TTLCacheMap>(20);

    Transaction(Connection conn)
    {
        _conn = conn;
    }

    public Connection getConnection()
    {
        return _conn;
    }

    public Map<Object, TTLCacheMap> getCaches()
    {
        return _transactionCaches;
    }
}
