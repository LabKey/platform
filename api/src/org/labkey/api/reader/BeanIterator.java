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
package org.labkey.api.reader;

import org.labkey.api.util.CloseableIterator;
import org.labkey.api.data.ObjectFactory;

import java.util.Map;
import java.io.IOException;

/**
* User: adam
* Date: May 5, 2009
* Time: 7:35:51 PM
*/

// Iterator that transforms Map<String, Object> to bean using ObjectFactory
public class BeanIterator<T> implements CloseableIterator<T>
{
    private CloseableIterator<Map<String, Object>> _mapIter;
    private ObjectFactory<T> _factory;

    public BeanIterator(CloseableIterator<Map<String, Object>> mapIter, Class<T> clazz)
    {
        _mapIter = mapIter;
        _factory = ObjectFactory.Registry.getFactory(clazz);
    }

    public void close() throws IOException
    {
        _mapIter.close();
    }

    public boolean hasNext()
    {
        return _mapIter.hasNext();
    }

    public T next()
    {
        Map<String, Object> row = _mapIter.next();
        return _factory.fromMap(row);
    }

    public void remove()
    {
        _mapIter.remove();
    }
}
