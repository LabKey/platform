/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.IdentifiableBase;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static org.labkey.api.data.CompareType.STARTS_WITH;

/**
 * User: jeckels
 * Date: Sep 26, 2007
 */
public abstract class ExpIdentifiableBaseImpl<Type extends IdentifiableBase> extends ExpObjectImpl
{
    protected Type _object;
    protected String _prevLsid = null;

    // For serialization
    protected ExpIdentifiableBaseImpl() {}

    public ExpIdentifiableBaseImpl(Type object)
    {
        _object = object;
        if (null != _object && null != _object.getObjectId())
            _objectId = _object.getObjectId();
    }

    @Override
    public String getLSID()
    {
        return _object.getLSID();
    }

    public Type getDataObject()
    {
        return _object;
    }

    @Override
    public void setLSID(Lsid lsid)
    {
        ensureUnlocked();
        setLSID(lsid == null ? null : lsid.toString());
    }

    @Override
    public void setLSID(String lsid)
    {
        ensureUnlocked();
        if (!Objects.equals(_object.getLSID(), lsid))
        {
            _prevLsid = _object.getLSID();
        }
        _object.setLSID(lsid);
    }

    @Override
    public String getName()
    {
        return _object.getName();
    }

    @Override
    public void setName(String name)
    {
        ensureUnlocked();
        _object.setName(name);
    }

    /**
     * Get the objectId used as the value in the exp.object.ownerObjectId column
     * e.g., for Material in a SampleType, this value is the SampleType's objectId.
     */
    public @Nullable Integer getParentObjectId()
    {
        return null;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ExpIdentifiableBaseImpl that = (ExpIdentifiableBaseImpl) o;

        return !(_object.getLSID() != null ? !_object.getLSID().equals(that._object.getLSID()) : that._object.getLSID() != null);
    }

    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + (_object.getLSID() != null ? _object.getLSID().hashCode() : 0);
        return result;
    }

    protected void save(User user, TableInfo table, boolean ensureObject)
    {
        save(user, table, ensureObject, getRowId() == 0);
    }

    protected void save(User user, TableInfo table, boolean ensureObject, boolean isInsert)
    {
        if (isInsert)
        {
            if (ensureObject)
            {
                assert !(_object instanceof IdentifiableEntity) || null == ((IdentifiableEntity)_object).getObjectId();
                _objectId = OntologyManager.ensureObject(getContainer(), getLSID(), getParentObjectId());
                _object.setObjectId(_objectId);
            }
            _object = Table.insert(user, table, _object);
            assert !ensureObject || !(_object instanceof IdentifiableEntity) || _objectId == ((IdentifiableEntity)_object).getObjectId();
        }
        else
        {
            try (DbScope.Transaction tx = table.getSchema().getScope().ensureTransaction())
            {
                // Create a new exp.object if the LSID changed
                if (_prevLsid != null && ensureObject)
                {
                    assert !Objects.equals(_prevLsid, getLSID());
                    _objectId = OntologyManager.ensureObject(getContainer(), getLSID(), getParentObjectId());
                    _object.replaceExistingObjectId(_objectId);
                }

                _object = Table.update(user, table, _object, getRowId());

                if (_prevLsid != null && ensureObject)
                {
                    OntologyManager.deleteOntologyObject(_prevLsid, getContainer(), true);
                }

                tx.commit();
            }
        }

        _prevLsid = null;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + ": " + _object.getName() + " - " + _object.getLSID();
    }

    protected Function<String, Long> getMaxCounterWithPrefixFunction(TableInfo tableInfo)
    {
        String dataTypeLsid = getLSID();
        return (namePrefix) ->
        {
            long max = 0;

            // Here we don't apply a container filter and instead rely on the "CpasType" of the associated data.
            // This allows for us to process max counter from all matching results within the provided type.
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("CpasType"), dataTypeLsid);
            filter.addCondition(FieldKey.fromParts("Name"), namePrefix, STARTS_WITH);

            TableSelector selector = new TableSelector(tableInfo, Collections.singleton("Name"), filter, null);
            final List<String> nameSuffixes = new ArrayList<>();
            selector.forEach(String.class, fullname -> nameSuffixes.add(fullname.replace(namePrefix, "")));

            for (String nameSuffix : nameSuffixes)
            {
                if (nameSuffix.matches("\\d+"))
                {
                    long id = Long.parseLong(nameSuffix);
                    if (id > max)
                        max = id;
                }
            }

            return max;
        };
    }

}
