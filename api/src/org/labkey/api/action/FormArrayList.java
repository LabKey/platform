/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.api.action;

import java.util.ArrayList;

/**
 * Useful List implementation for form beans
 * User: matthewb
 * Date: May 29, 2007
 */
public class FormArrayList<T> extends ArrayList<T>
{
    protected int maxSize = 1024;           // defensive programming
    final Class<? extends T> _class;

    public FormArrayList(Class<? extends T> listClass)
    {
        _class = listClass;
    }

    @Override
    public T get(int index)
    {
        try
        {
            if (index >= maxSize)
                throw new IndexOutOfBoundsException();
            while (size() <= index)
                add(null);
            if (null == super.get(index))
                set(index, newInstance());
            return super.get(index);
        }
        catch (InstantiationException | IllegalAccessException x)
        {
            throw new RuntimeException(x);
        }
    }

    @Override
    public T set(int i, T t)
    {
        while (size() < i+1)
            add(null);
        return super.set(i,t);
    }

    protected T newInstance() throws IllegalAccessException, InstantiationException
    {
        return _class.newInstance();
    }

    public void setMaxSize(int max)
    {
        maxSize = max;
    }
}
