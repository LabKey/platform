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
import org.labkey.api.util.Filter;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

/**
 * User: adam
 * Date: May 5, 2009
 * Time: 7:27:47 PM
 */
public class BeanTabLoader<T> extends AbstractTabLoader<T>
{
    private Filter<T> _beanFilter = null;
    private final Class<T> _clazz;

    // Infer whether there are headers
    public BeanTabLoader(Class<T> clazz, File inputFile) throws IOException
    {
        super(inputFile, null);
        _clazz = clazz;
    }

    public BeanTabLoader(Class<T> clazz, File inputFile, boolean hasColumnHeaders) throws IOException
    {
        super(inputFile, hasColumnHeaders);
        _clazz = clazz;
    }

    public BeanTabLoader(Class<T> clazz, Reader reader, boolean hasColumnHeaders) throws IOException
    {
        super(reader, hasColumnHeaders);
        _clazz = clazz;
    }

    public BeanTabLoader(Class<T> clazz, CharSequence src, boolean hasColumnHeaders) throws IOException
    {
        super(src, hasColumnHeaders);
        _clazz = clazz;
    }

    public void setBeanFilter(Filter<T> beanFilter)
    {
        _beanFilter = beanFilter;
    }

    public CloseableIterator<T> iterator()
    {
        if (null == _beanFilter)
            return new BeanIterator<T>(mapIterator(), _clazz);
        else
            return new CloseableFilterIterator<T>(new BeanIterator<T>(mapIterator(), _clazz), _beanFilter);
    }
}
