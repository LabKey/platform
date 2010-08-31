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
package org.labkey.api.iterator;

import org.labkey.api.util.Filter;

import java.io.IOException;

/**
* User: adam
* Date: Aug 20, 2010
* Time: 12:40:48 PM
*/
public class CloseableFilteredIterator<T> extends FilteredIterator<T> implements CloseableIterator<T>
{
    protected CloseableIterator<T> _iter;

    public CloseableFilteredIterator(CloseableIterator<T> iter, Filter<T> filter)
    {
        super(iter, filter);
        _iter = iter;
    }

    public void close() throws IOException
    {
        _iter.close();
    }
}
