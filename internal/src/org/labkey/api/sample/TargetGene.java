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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.util.ResultSetUtil;

/**
 * Bean Class for for TargetGene.
 *
 * @author <a href="http://boss.bekk.no/boss/middlegen/"/>Middlegen</a>
 */
public class TargetGene
{

    private int _targetGeneId = 0;
    private java.lang.String _targetGene = null;
    private java.lang.String _tgCharacteristic = null;
    private java.sql.Timestamp _dateAdded = null;

    public TargetGene()
    {
    }

    public int getTargetGeneId()
    {
        return _targetGeneId;
    }

    public void setTargetGeneId(int targetGeneId)
    {
        _targetGeneId = targetGeneId;
    }

    public java.lang.String getTargetGene()
    {
        return _targetGene;
    }

    public void setTargetGene(java.lang.String targetGene)
    {
        _targetGene = targetGene;
    }

    public java.lang.String getTgCharacteristic()
    {
        return _tgCharacteristic;
    }

    public void setTgCharacteristic(java.lang.String tgCharacteristic)
    {
        _tgCharacteristic = tgCharacteristic;
    }

    public java.sql.Timestamp getDateAdded()
    {
        return _dateAdded;
    }

    public void setDateAdded(java.sql.Timestamp dateAdded)
    {
        _dateAdded = dateAdded;
    }


}
