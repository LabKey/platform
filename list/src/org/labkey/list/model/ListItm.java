/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

package org.labkey.list.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.UnexpectedException;

public class ListItm implements Cloneable
{
    private int listId;
    private Object key;
    private Integer objectId;
    private String entityId;

    public Object getKey()
    {
        return key;
    }

    public void setKey(Object key)
    {
        this.key = key;
    }

    public int getListId()
    {
        return listId;
    }

    public void setListId(int listId)
    {
        this.listId = listId;
    }

    @Nullable
    @Deprecated
    public Integer getObjectId()
    {
        return objectId;
    }

    @Deprecated
    public void setObjectId(Integer objectId)
    {
        this.objectId = objectId;
    }

    public String getEntityId()
    {
        return entityId;
    }

    public void setEntityId(String entityId)
    {
        this.entityId = entityId;
    }

    @Override
    protected ListItm clone()
    {
        try
        {
            return (ListItm) super.clone();
        }
        catch (CloneNotSupportedException cnse)
        {
            throw UnexpectedException.wrap(cnse);
        }
    }
}
