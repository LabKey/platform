/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

/**
* User: adam
* Date: 1/15/12
* Time: 10:23 PM
*/
public class Wrapper<V>
{
    @SuppressWarnings({"unchecked"})
    protected V value = (V) BlockingCache.UNINITIALIZED;
    protected boolean loading = false;

    void setLoading()
    {
        loading = true;
    }

    // call in finally
    void doneLoading()
    {
        loading = false;
    }

    void loadFailed()
    {
        value =  (V) BlockingCache.UNINITIALIZED;
        doneLoading();
    }

    boolean isLoading()
    {
        return loading;
    }

    void setValue(V v)
    {
        value = v;
        doneLoading();
    }

    public V getValue()
    {
        return value == BlockingCache.UNINITIALIZED ? null : value;
    }
}
