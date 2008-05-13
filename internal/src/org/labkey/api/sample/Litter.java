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
 * Bean Class for for Litter.
 *
 * @author <a href="http://boss.bekk.no/boss/middlegen/"/>Middlegen</a>
 */
public class Litter
{

    private int _litterId = 0;
    private int _breedingPairId = 0;
    private String _breedingPairIdDisplay = null;
    private String name;
    private java.lang.String _container = null;
    private java.sql.Timestamp _birthDate = null;
    private int _males = 0;
    private int _females = 0;

    public Litter()
    {
    }

    public int getLitterId()
    {
        return _litterId;
    }

    public void setLitterId(int litterId)
    {
        _litterId = litterId;
    }

    public int getBreedingPairId()
    {
        return _breedingPairId;
    }

    public void setBreedingPairId(int breedingPairId)
    {
        _breedingPairId = breedingPairId;
    }

    public java.lang.String getContainer()
    {
        return _container;
    }

    public void setContainer(java.lang.String container)
    {
        _container = container;
    }

    public java.sql.Timestamp getBirthDate()
    {
        return _birthDate;
    }

    public void setBirthDate(java.sql.Timestamp birthDate)
    {
        _birthDate = birthDate;
    }

    public int getMales()
    {
        return _males;
    }

    public void setMales(int males)
    {
        _males = males;
    }

    public int getFemales()
    {
        return _females;
    }

    public void setFemales(int females)
    {
        _females = females;
    }


    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }
}
