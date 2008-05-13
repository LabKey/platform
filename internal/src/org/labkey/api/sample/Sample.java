/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.sample;

import org.labkey.api.exp.IdentifiableBase;

/**
 * Bean Class for for Sample.
 *
 * @author <a href="http://boss.bekk.no/boss/middlegen/"/>Middlegen</a>
 */
public class Sample extends IdentifiableBase
{

    private java.lang.String _ts = null;
    private int _createdBy = 0;
    private java.sql.Timestamp _created = null;
    private int _modifiedBy = 0;
    private java.sql.Timestamp _modified = null;
    private java.lang.String _container = null;
    private java.lang.String _organismId = null;
    private java.lang.String _lsid = null;
    private java.lang.String _sampleId = null;
    private int _sampleTypeId = 0;
    private java.lang.String _entityId = null;
    private java.sql.Timestamp _collectionDate = null;
    private java.lang.String _description = null;
    private boolean _fixed;
    private boolean _frozen;
    private boolean _frozenUsed;

    public Sample()
    {
    }

    public java.lang.String getTs()
    {
        return _ts;
    }

    public void setTs(java.lang.String ts)
    {
        _ts = ts;
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        _createdBy = createdBy;
    }

    public java.sql.Timestamp getCreated()
    {
        return _created;
    }

    public void setCreated(java.sql.Timestamp created)
    {
        _created = created;
    }

    public int getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(int modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public java.sql.Timestamp getModified()
    {
        return _modified;
    }

    public void setModified(java.sql.Timestamp modified)
    {
        _modified = modified;
    }

    public java.lang.String getContainer()
    {
        return _container;
    }

    public void setContainer(java.lang.String container)
    {
        _container = container;
    }

    public java.lang.String getOrganismId()
    {
        return _organismId;
    }

    public void setOrganismId(java.lang.String organismId)
    {
        _organismId = organismId;
    }

    public java.lang.String getSampleId()
    {
        return _sampleId;
    }

    public void setSampleId(java.lang.String sampleId)
    {
        _sampleId = sampleId;
    }

    public int getSampleTypeId()
    {
        return _sampleTypeId;
    }

    public void setSampleTypeId(int sampleTypeId)
    {
        _sampleTypeId = sampleTypeId;
    }

    public java.lang.String getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(java.lang.String entityId)
    {
        _entityId = entityId;
    }

    public java.sql.Timestamp getCollectionDate()
    {
        return _collectionDate;
    }

    public void setCollectionDate(java.sql.Timestamp collectionDate)
    {
        _collectionDate = collectionDate;
    }

    public java.lang.String getDescription()
    {
        return _description;
    }

    public void setDescription(java.lang.String description)
    {
        _description = description;
    }

    public boolean getFixed()
    {
        return _fixed;
    }

    public void setFixed(boolean fixed)
    {
        _fixed = fixed;
    }

    public boolean getFrozen()
    {
        return _frozen;
    }

    public void setFrozen(boolean frozen)
    {
        _frozen = frozen;
    }

    public boolean getFrozenUsed()
    {
        return _frozenUsed;
    }

    public void setFrozenUsed(boolean frozenUsed)
    {
        _frozenUsed = frozenUsed;
    }
}
