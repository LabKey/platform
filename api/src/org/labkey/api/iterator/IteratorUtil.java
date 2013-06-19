/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
package org.labkey.api.iterator;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * User: adam
 * Date: Aug 20, 2010
 * Time: 11:55:59 AM
 */
public class IteratorUtil
{
    // Returns a list of T records, one for each non-header row in the input.  Static convenience method intended
    // for use with BeanIterators.
    //
    // Caution: Using this instead of iterating directly has lead to many scalability problems in the past.
    public static <T> List<T> toList(CloseableIterator<T> it) throws IOException
    {
        List<T> list = new LinkedList<>();

        try
        {
            while (it.hasNext())
                list.add(it.next());
        }
        finally
        {
            it.close();
        }

        return list;
    }
}
