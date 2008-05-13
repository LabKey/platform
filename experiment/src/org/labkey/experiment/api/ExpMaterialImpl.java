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

import org.labkey.api.exp.api.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.*;

public class ExpMaterialImpl extends ExpIdentifiableBaseImpl<Material> implements ExpMaterial
{
    static public ExpMaterial[] fromMaterials(Material[] materials)
    {
        ExpMaterial[] ret = new ExpMaterial[materials.length];
        for (int i = 0; i < materials.length; i ++)
        {
            ret[i] = new ExpMaterialImpl(materials[i]);
        }
        return ret;
    }

    public ExpMaterialImpl(Material material)
    {
        super(material);
    }

    public int getRowId()
    {
        return _object.getRowId();
    }

    public URLHelper detailsURL()
    {
        ActionURL ret = new ActionURL("Experiment", "showMaterial", getContainerPath());
        ret.addParameter("rowId", Integer.toString(getRowId()));
        return ret;
    }

    public User getCreatedBy()
    {
        ExpRunImpl run = getRun();
        return null == run ? null : run.getCreatedBy();
    }

    public ExpSampleSet getSampleSet()
    {
        String type = _object.getCpasType();
        if (!"Material".equals(type) && !"Sample".equals(type))
        {
            return ExperimentService.get().getSampleSet(type);
        }
        else
        {
            return null;
        }
    }

    public String getContainerId()
    {
        return _object.getContainer();
    }

    public void setContainerId(String containerId)
    {
        _object.setContainer(containerId);
    }
    
    public void insert(User user) throws SQLException
    {
        ExperimentServiceImpl.get().insertMaterial(user, _object);
    }

    public ExpRunImpl getRun()
    {
        if (_object.getRunId() == null)
        {
            return null;
        }
        return ExperimentServiceImpl.get().getExpRun(_object.getRunId());
    }

    public void setSourceApplication(ExpProtocolApplication sourceApplication)
    {
        if (sourceApplication != null && sourceApplication.getRowId() == 0)
        {
            throw new IllegalArgumentException();
        }
        _object.setSourceApplicationId(sourceApplication == null ? null : sourceApplication.getRowId());
    }

    public void setSourceProtocol(ExpProtocol protocol)
    {
        if (protocol != null && protocol.getLSID() == null)
        {
            throw new IllegalArgumentException();
        }
        _object.setSourceProtocolLSID(protocol == null ? null : protocol.getLSID());
    }

    public void setRun(ExpRun run)
    {
        if (run != null && run.getRowId() == 0)
        {
            throw new IllegalArgumentException();
        }
        _object.setRunId(run == null ? null : run.getRowId());
    }

    public List<ExpProtocolApplication> retrieveSuccessorAppList()
    {
        return _object.retrieveSuccessorAppList();
    }

    public List<Integer> retrieveSuccessorRunIdList()
    {
        return _object.retrieveSuccessorRunIdList();
    }

    public void storeSuccessorAppList(ArrayList<ExpProtocolApplication> protocolApplications)
    {
        _object.storeSuccessorAppList(protocolApplications);
    }

    public void storeSourceApp(ExpProtocolApplication protocolApplication)
    {
        _object.storeSourceApp(protocolApplication);
    }

    public void storeSuccessorRunIdList(ArrayList<Integer> integers)
    {
        _object.storeSuccessorRunIdList(integers);
    }

    public ExpProtocolApplication retrieveSourceApp()
    {
        return _object.retrieveSourceApp();
    }

    public void setCpasType(String type)
    {
        _object.setCpasType(type);
    }

    public Map<PropertyDescriptor, Object> getPropertyValues()
    {
        ExpSampleSet sampleSet = getSampleSet();
        if (sampleSet == null)
        {
            return Collections.emptyMap();
        }
        PropertyDescriptor[] pds = sampleSet.getPropertiesForType();
        Map<PropertyDescriptor, Object> values = new HashMap<PropertyDescriptor, Object>();
        for (PropertyDescriptor pd : pds)
        {
            values.put(pd, getProperty(pd));
        }
        return values;
    }

    public ExpProtocolApplication getSourceApplication()
    {
        if (_object.getSourceApplicationId() == null)
        {
            return null;
        }
        return ExperimentService.get().getExpProtocolApplication(_object.getSourceApplicationId());
    }

    public ExpProtocol getSourceProtocol()
    {
        if (_object.getSourceProtocolLSID() == null)
        {
            return null;
        }
        return ExperimentService.get().getExpProtocol(_object.getSourceProtocolLSID());
    }


    public String getCpasType()
    {
        String result = _object.getCpasType();
        return result == null ? "Material" : result;
    }
}
