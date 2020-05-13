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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

abstract public class ExpObjectImpl implements ExpObject, Serializable
{
    protected boolean _locked = false;
    // TODO: add objectId column to the exp.* tables that need it and to IdentifiableBase java bean
    protected int _objectId;

    // For serialization
    protected ExpObjectImpl() {}

    public void lock()
    {
        _locked = true;
    }

    protected void ensureUnlocked()
    {
        if (_locked)
        {
            throw new IllegalStateException("Cannot change a locked " + getClass());
        }
    }

    @Override
    public final @Nullable Integer getObjectId()
    {
        // hasn't been inserted yet
        if (getRowId() == 0)
            return null;

        if (_objectId == 0)
        {
            OntologyObject oo = OntologyManager.getOntologyObject(getContainer(), getLSID());
            if (oo == null)
                return null;
            _objectId = oo.getObjectId();
        }

        return _objectId;
    }

    public String getComment()
    {
        return (String) getProperty(ExperimentProperty.COMMENT.getPropertyDescriptor());
    }

    /**
     * @return Map from PropertyURI to ObjectProperty.value
     */
    public Map<String, Object> getProperties()
    {
        return OntologyManager.getProperties(getContainer(), getLSID());
    }

    /**
     * @return Map from PropertyURI to ObjectProperty
     */
    public Map<String, ObjectProperty> getObjectProperties()
    {
        return OntologyManager.getPropertyObjects(getContainer(), getLSID());
    }

    public void setComment(User user, String comment) throws ValidationException
    {
        comment = StringUtils.trimToNull(comment);
        try
        {
            setProperty(user, ExperimentProperty.COMMENT.getPropertyDescriptor(), comment);
        }
        catch (RuntimeException e)
        {
            // sometimes multiple threads attempt to set the same comment.
            // Don't throw an exception if the comment was actually set to the correct value.
            if (Objects.equals(comment, getComment()))
            {
                return;
            }
            throw e;
        }
    }

    protected String getOwnerObjectLSID()
    {
        return getOwnerObject().getLSID();
    }

    protected ExpObject getOwnerObject()
    {
        return this;
    }

    public void setProperty(User user, PropertyDescriptor pd, Object value) throws ValidationException
    {
        if (pd.getPropertyType() == PropertyType.RESOURCE)
            throw new IllegalArgumentException("PropertyType resource is NYI in this method");
        OntologyManager.updateObjectProperty(user, getContainer(), pd, getLSID(), value, this);
    }

    public String urlFlag(boolean flagged)
    {
        return AppProps.getInstance().getContextPath() + "/experiment/" + (flagged ? "flagDefault.gif" : "unflagDefault.gif");
    }

    public Object getProperty(DomainProperty prop)
    {
        return getProperty(prop.getPropertyDescriptor());
    }

    public Object getProperty(PropertyDescriptor pd)
    {
        if (pd == null)
            return null;

        Map<String, Object> properties = OntologyManager.getProperties(getContainer(), getLSID());
        Object value = properties.get(pd.getPropertyURI());
        if (value == null)
            return null;
        if (pd.getPropertyType() == PropertyType.RESOURCE)
        {
            return new ExpChildObjectImpl(getOwnerObject(), this, pd, (String) value);
        }
        return value;
    }

    public boolean equals(Object obj)
    {
        if (obj == null || obj.getClass() != getClass())
            return false;
        return ((ExpObjectImpl) obj).getRowId() == getRowId();
    }

    public int hashCode()
    {
        return getRowId() ^ getClass().hashCode();
    }

    public int compareTo(ExpObject o2)
    {
        if (getName() != null)
        {
            if (o2.getName() != null)
            {
                return getName().compareToIgnoreCase(o2.getName());
            }
            return 1;
        }
        else
        {
            if (o2.getName() != null)
            {
                return -1;
            }
            return 0;
        }
    }
}
