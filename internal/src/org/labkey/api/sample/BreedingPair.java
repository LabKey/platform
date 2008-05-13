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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.util.ResultSetUtil;

/**
 * Bean Class for for BreedingPair.
 *
 * @author <a href="http://boss.bekk.no/boss/middlegen/"/>Middlegen</a>
 */
public class BreedingPair
{

    private int _breedingPairId = 0;
    private int _modelId = 0;
    private String _modelIdDisplay = null;
    private java.lang.String _pairName = null;
    private java.lang.String _container = null;
    private java.lang.String _entityId = null;
    private java.sql.Timestamp _dateJoined = null;
    private java.lang.String _maleNotes = null;
    private java.lang.String _femaleNotes = null;

    public BreedingPair()
    {
    }

    public int getBreedingPairId()
    {
        return _breedingPairId;
    }

    public void setBreedingPairId(int breedingPairId)
    {
        _breedingPairId = breedingPairId;
    }

    public int getModelId()
    {
        return _modelId;
    }

    public void setModelId(int modelId)
    {
        _modelId = modelId;
    }

    public java.lang.String getPairName()
    {
        return _pairName;
    }

    public void setPairName(java.lang.String pairName)
    {
        _pairName = pairName;
    }

    public java.lang.String getContainer()
    {
        return _container;
    }

    public void setContainer(java.lang.String container)
    {
        _container = container;
    }

    public java.lang.String getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(java.lang.String entityId)
    {
        _entityId = entityId;
    }

    public java.sql.Timestamp getDateJoined()
    {
        return _dateJoined;
    }

    public void setDateJoined(java.sql.Timestamp dateJoined)
    {
        _dateJoined = dateJoined;
    }

    public java.lang.String getMaleNotes()
    {
        return _maleNotes;
    }

    public void setMaleNotes(java.lang.String maleNotes)
    {
        _maleNotes = maleNotes;
    }

    public java.lang.String getFemaleNotes()
    {
        return _femaleNotes;
    }

    public void setFemaleNotes(java.lang.String femaleNotes)
    {
        _femaleNotes = femaleNotes;
    }


}
