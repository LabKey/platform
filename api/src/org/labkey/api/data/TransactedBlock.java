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

import java.sql.SQLException;

/*
* User: adam
* Date: Aug 25, 2009
* Time: 12:07:55 PM
*/
public abstract class TransactedBlock<T>
{
    abstract public T block();

    public final T run(DbSchema schema) throws SQLException
    {
        DbScope scope = schema.getScope();

        boolean startedTransaction = false;

        try
        {
            if (!scope.isTransactionActive())
            {
                scope.beginTransaction();
                startedTransaction = true;
            }

            T ret = block();

            if (startedTransaction)
                scope.commitTransaction();

            return ret;
        }
        finally
        {
            if (startedTransaction)
                scope.closeConnection();
        }
    }
}
