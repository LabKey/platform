/*
 * Copyright (c) 2004-2007 Fred Hutchinson Cancer Research Center
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

import java.io.Serializable;
import java.util.*;
import java.sql.SQLException;

import org.apache.log4j.Logger;
import org.labkey.api.data.*;

/**
 * Bean Class for for MouseModel.
 *
 * @author <a href="http://boss.bekk.no/boss/middlegen/"/>Middlegen</a>
 */
public class MouseModel implements Serializable
{

    private java.lang.String _ts = null;
    private int _createdBy = 0;
    private java.sql.Timestamp _created = null;
    private int _modifiedBy = 0;
    private java.sql.Timestamp _modified = null;
    private int _modelId = 0;
    private String _entityId = null;
    private java.lang.String _container = null;
    private java.lang.String _name = null;
    private java.lang.String _emiceId = null;
    private java.lang.String _tumorType = null;
    private int _targetGeneId = 0;
    private int _mouseStrainId = 0;
    private boolean _metastasis = false;
    private java.lang.String _penetrance = null;
    private java.lang.String _latency = null;
    private java.lang.String _location = null;
    private java.lang.String _investigator = null;
    private int _genotypeId = 0;
    private int _irradDoseId = 0;
    private int _treatmentId = 0;
    private java.lang.String _preNecroChem = null;
    private boolean _brduAtDeath = false;
    private String _materialSourceLSID = null;

    //Extra per-mouse fields that can be used with different titles
    private String _int1Caption = null;
    private String _int2Caption = null;
    private String _int3Caption = null;
    private String _date1Caption = null;
    private String _date2Caption = null;
    private String _string1Caption = null;
    private String _string2Caption = null;
    private String _string3Caption = null;

    public MouseModel()
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

    public int getModelId()
    {
        return _modelId;
    }

    public void setModelId(int modelId)
    {
        _modelId = modelId;
    }

    public java.lang.String getContainer()
    {
        return _container;
    }

    public java.lang.String getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(java.lang.String entityId)
    {
        _entityId = entityId;
    }

    public void setContainer(java.lang.String container)
    {
        _container = container;
    }

    public java.lang.String getName()
    {
        return _name;
    }

    public void setName(java.lang.String name)
    {
        _name = name;
    }

    public java.lang.String getEmiceId()
    {
        return _emiceId;
    }

    public void setEmiceId(java.lang.String emiceId)
    {
        _emiceId = emiceId;
    }

    public java.lang.String getTumorType()
    {
        return _tumorType;
    }

    public void setTumorType(java.lang.String tumorType)
    {
        _tumorType = tumorType;
    }

    public int getTargetGeneId()
    {
        return _targetGeneId;
    }

    public void setTargetGeneId(int targetGeneId)
    {
        _targetGeneId = targetGeneId;
    }

    public String getTargetGene()
    {
        try
        {
            return MouseSchema.getTargetGene().getRowTitle(_targetGeneId);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public int getMouseStrainId()
    {
        return _mouseStrainId;
    }

    public void setMouseStrainId(int mouseStrainId)
    {
        _mouseStrainId = mouseStrainId;
    }

    public String getMouseStrain()
    {
        try
        {
            return MouseSchema.getMouseStrain().getRowTitle(_mouseStrainId);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public boolean getMetastasis()
    {
        return _metastasis;
    }

    public void setMetastasis(boolean metastasis)
    {
        _metastasis = metastasis;
    }

    public java.lang.String getPenetrance()
    {
        return _penetrance;
    }

    public void setPenetrance(java.lang.String penetrance)
    {
        _penetrance = penetrance;
    }

    public java.lang.String getLatency()
    {
        return _latency;
    }

    public void setLatency(java.lang.String latency)
    {
        _latency = latency;
    }

    public java.lang.String getLocation()
    {
        return _location;
    }

    public void setLocation(java.lang.String location)
    {
        _location = location;
    }

    public java.lang.String getInvestigator()
    {
        return _investigator;
    }

    public void setInvestigator(java.lang.String investigator)
    {
        _investigator = investigator;
    }

    public int getGenotypeId()
    {
        return _genotypeId;
    }

    public void setGenotypeId(int genotypeId)
    {
        _genotypeId = genotypeId;
    }

    public String getGenotype()
    {
        try
        {
            return MouseSchema.getGenotype().getRowTitle(_genotypeId);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public int getIrradDoseId()
    {
        return _irradDoseId;
    }

    public void setIrradDoseId(int irradDoseId)
    {
        _irradDoseId = irradDoseId;
    }

    public String getIrradDose()
    {
        try
        {
            return MouseSchema.getIrradDose().getRowTitle(_irradDoseId);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public int getTreatmentId()
    {
        return _treatmentId;
    }

    public void setTreatmentId(int treatmentId)
    {
        _treatmentId = treatmentId;
    }

    public String getTreatment()
    {
        try
        {
            return MouseSchema.getTreatment().getRowTitle(_treatmentId);
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public java.lang.String getPreNecroChem()
    {
        return _preNecroChem;
    }

    public void setPreNecroChem(java.lang.String preNecroChem)
    {
        _preNecroChem = preNecroChem;
    }

    public boolean getBrduAtDeath()
    {
        return _brduAtDeath;
    }

    public void setBrduAtDeath(boolean brduAtDeath)
    {
        _brduAtDeath = brduAtDeath;
    }

    public String getInt1Caption()
    {
        return _int1Caption;
    }

    public void setInt1Caption(String _int1Caption)
    {
        this._int1Caption = _int1Caption;
    }

    public String getInt2Caption()
    {
        return _int2Caption;
    }

    public void setInt2Caption(String _int2Caption)
    {
        this._int2Caption = _int2Caption;
    }

    public String getInt3Caption()
    {
        return _int3Caption;
    }

    public void setInt3Caption(String _int3Caption)
    {
        this._int3Caption = _int3Caption;
    }

    public String getDate1Caption()
    {
        return _date1Caption;
    }

    public void setDate1Caption(String _date1Caption)
    {
        this._date1Caption = _date1Caption;
    }

    public String getDate2Caption()
    {
        return _date2Caption;
    }

    public void setDate2Caption(String _date2Caption)
    {
        this._date2Caption = _date2Caption;
    }

    public String getString1Caption()
    {
        return _string1Caption;
    }

    public void setString1Caption(String _string1Caption)
    {
        this._string1Caption = _string1Caption;
    }

    public String getString2Caption()
    {
        return _string2Caption;
    }

    public void setString2Caption(String _string2Caption)
    {
        this._string2Caption = _string2Caption;
    }

    public String getString3Caption()
    {
        return _string3Caption;
    }

    public void setString3Caption(String _string3Caption)
    {
        this._string3Caption = _string3Caption;
    }

    private static final String [] extraColumnNames = {"int1", "int2", "int3", "date1", "date2", "string1", "string2", "string3"};

    public Map getExtraColumnCaptions()
    {
        Map map = new HashMap();
        String[] columnCaptions = new String[]{
                _int1Caption, _int2Caption, _int3Caption,
                _date1Caption, _date2Caption,
                _string1Caption, _string2Caption, _string3Caption
        };
        for (int i = 0; i < columnCaptions.length; i++)
            if (null != columnCaptions[i] && !"".equals(columnCaptions[i]))
                map.put(extraColumnNames[i], columnCaptions[i]);

        return map;
    }

    public String getMaterialSourceLSID()
    {
        return _materialSourceLSID;
    }

    public void setMaterialSourceLSID(String materialSourceLSID)
    {
        this._materialSourceLSID = materialSourceLSID;
    }
}
