/*
 * Copyright (c) 2005-2012 Fred Hutchinson Cancer Research Center
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
package org.labkey.experiment.api;

import org.labkey.api.data.Container;
import org.labkey.api.util.GUID;
import org.labkey.api.security.User;
import org.labkey.api.exp.IdentifiableBase;
import org.labkey.api.exp.Identifiable;

import java.util.*;

/**
 * Base class for beans that have some standard fields, like RowId and Container.
 * User: migra
 * Date: Jun 14, 2005
 */
public class IdentifiableEntity extends IdentifiableBase implements Identifiable
{
    private int rowId;
    protected String entityId;
    private int createdBy;
    private long created = 0;
    private int modifiedBy;
    private long modified;
    private Container container;

    protected IdentifiableEntity()
    {
    }


    public void beforeSave(User user, Container c)
    {
        if (created == 0)
            beforeInsert(user, c);
        else
            beforeUpdate(user);
    }


    public void beforeUpdate(User user)
    {
        if (user != null)
            modifiedBy = user.getUserId();
        modified = System.currentTimeMillis();
    }

    public void beforeUpdate(User user, IdentifiableEntity cur)
    {
        entityId = cur.entityId;
        container = cur.container;
        createdBy = cur.createdBy;
        created = cur.created;

        beforeUpdate(user);
    }

    public void beforeInsert(User user, Container container)
    {
        if (null == entityId)
            entityId = GUID.makeGUID();
        this.container = container;
        if (user != null)
            createdBy = user.getUserId();
        created = System.currentTimeMillis();
        modifiedBy = createdBy;
        modified = created;
    }

    public int getCreatedBy()
    {
        return createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        this.createdBy = createdBy;
    }

    public Date getCreated()
    {
        if (created == 0)
            return null;
        return new Date(created);
    }

    public void setCreated(Date created)
    {
        this.created = created == null ? 0 : created.getTime();
    }

    public int getModifiedBy()
    {
        return modifiedBy;
    }

    public void setModifiedBy(int modifiedBy)
    {
        this.modifiedBy = modifiedBy;
    }

    public Date getModified()
    {
        return new Date(modified);
    }

    public void setModified(Date modified)
    {
        this.modified = modified == null ? 0 : modified.getTime();
    }

    public int getRowId()
    {
        return rowId;
    }

    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }

    public Container getContainer()
    {
        return container;
    }

    // for Table layer
    public void setContainer(Container container)
    {
        this.container = container;
    }

    public String getEntityId()
    {
        return entityId;
    }

    public void setEntityId(String entityId)
    {
        if (this.entityId != null && !this.entityId.equals(entityId))
            throw new IllegalStateException("can't change entityid");
        this.entityId = entityId;
    }

    public static boolean diff(int i1, int i2, String name, List<Difference> diffs)
    {
        if (i1 != i2)
        {
            diffs.add(new Difference(name, i1, i2));
            return true;
        }
        return false;
    }

    public static boolean diff(Object o1, Object o2, String name, List<Difference> diffs)
    {
        if ((o1 != o2) && !(o1 != null && o1.equals(o2)))
        {
            diffs.add(new Difference(name, o1, o2));
            return true;
        }

        return false;
    }

    public static boolean diff(String s1, String s2, String name, List<Difference> diffs)
    {
        if ((s1 != s2) && !(s1 != null && s1.equals(s2)))
        {
            if (!(s1 == null && "".equals(s2)) &&
                !(s2 == null && "".equals(s1)))
            {
                diffs.add(new Difference(name, s1, s2));
                return true;
            }
        }
        return false;
    }

    public static class Difference
    {
        private String _description;
        private Object _thisValue;
        private Object _otherValue;

        public Difference(String description, Object thisValue, Object otherValue)
        {
            _description = description;
            _thisValue = thisValue;
            _otherValue = otherValue;
        }

        public String toString()
        {
            return _description + " differs: '" + _thisValue + "' vs '" + _otherValue + "'";
        }

    }
}
