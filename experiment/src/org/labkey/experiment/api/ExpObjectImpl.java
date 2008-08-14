/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpChildObject;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.*;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.GUID;
import org.apache.log4j.Logger;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.ObjectUtils;

import java.util.Map;
import java.util.Collections;
import java.sql.SQLException;
import java.io.Serializable;

abstract public class ExpObjectImpl implements ExpObject, Serializable
{
    static final private Logger _log = Logger.getLogger(ExpObjectImpl.class);
    static public final String s_urlFlagged = AppProps.getInstance().getContextPath() + "/Experiment/flagDefault.gif";
    static public final String s_urlUnflagged = AppProps.getInstance().getContextPath() + "/Experiment/unflagDefault.gif";
    abstract public String getContainerId();
    abstract public void setContainerId(String containerId);

    public Container getContainer()
    {
        return ContainerManager.getForId(getContainerId());
    }
    
    public void setContainer(Container container)
    {
        setContainerId(container.getId());
    }

    public String getContainerPath()
    {
        return getContainer().getPath();
    }

    public String getLSIDNamespacePrefix()
    {
        return new Lsid(getLSID()).getNamespacePrefix();
    }

    public String getComment()
    {
        return (String) getProperty(ExperimentProperty.COMMENT.getPropertyDescriptor());
    }

    public Map<String, Object> getProperties()
    {
        try
        {
            return OntologyManager.getProperties(getContainerId(), getLSID());
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void setComment(User user, String comment) throws Exception
    {
        comment = StringUtils.trimToNull(comment);
        try
        {
            setProperty(user, ExperimentProperty.COMMENT.getPropertyDescriptor(), comment);
        }
        catch (Exception e)
        {
            // sometimes multiple threads attempt to set the same comment.
            // Don't throw an exception if the comment was actually set to the correct value.
            if (ObjectUtils.equals(comment, getComment()))
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

    public void setProperty(User user, PropertyDescriptor pd, Object value) throws SQLException
    {
        if (pd.getPropertyType() == PropertyType.RESOURCE)
            throw new IllegalArgumentException("PropertyType resource is NYI in this method");
        boolean fTrans = false;
        try
        {
            if (!ExperimentService.get().isTransactionActive())
            {
                ExperimentService.get().beginTransaction();
                fTrans = true;
            }

            OntologyManager.deleteProperty(getLSID(), pd.getPropertyURI(), getContainer(), pd.getContainer());

            if (value != null)
            {
                ObjectProperty oprop = new ObjectProperty(getLSID(), getContainerId(), pd.getPropertyURI(), value, pd.getPropertyType());
                oprop.setPropertyId(pd.getPropertyId());
                OntologyManager.insertProperty(getContainerId(), oprop, getOwnerObjectLSID());
            }
            if (fTrans)
            {
                ExperimentService.get().commitTransaction();
                fTrans = false;
            }
        }
        finally
        {
            if (fTrans)
            {
                ExperimentService.get().rollbackTransaction();
            }
        }

    }

    public String urlFlag(boolean flagged)
    {
        return flagged ? s_urlFlagged : s_urlUnflagged;
    }

    public Object getProperty(PropertyDescriptor pd)
    {
        if (pd == null)
            return null;
        try
        {
            Map<String, Object> properties = OntologyManager.getProperties(getContainerId(), getLSID());
            Object value = properties.get(pd.getPropertyURI());
            if (value == null)
                return null;
            if (pd.getPropertyType() == PropertyType.RESOURCE)
            {
                return new ExpChildObjectImpl(getOwnerObject(), this, pd, (String) value);
            }
            return value;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public ExpChildObject createPropertyObject(PropertyDescriptor pd) throws Exception
    {
        if (pd.getPropertyType() != PropertyType.RESOURCE)
            throw new IllegalArgumentException("Not a child object property.");
        if (getProperty(pd) != null)
            throw new IllegalArgumentException("Property already exists");
        String objectURI = GUID.makeURN();
        ObjectProperty oprop = new ObjectProperty(getLSID(), getContainerId(), pd.getPropertyURI(), objectURI);
        OntologyManager.insertProperty(getContainerId(), oprop, getOwnerObjectLSID());
        return new ExpChildObjectImpl(getOwnerObject(), this, pd, objectURI);
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
}
