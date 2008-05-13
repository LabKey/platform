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

import org.apache.log4j.Logger;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;

import java.io.Serializable;

/**
 * Bean Class for for Cage.
 *
 * @author <a href="http://boss.bekk.no/boss/middlegen/"/>Middlegen</a>
 */
public class Cage implements Serializable
{

    private int _cageId = 0;
    private java.lang.String _cageName = null;
    private java.lang.String _container = null;
    private String _litterIdDisplay = null;
    private int _modelId = 0;
    private String _modelIdDisplay = null;
    private java.lang.String _sex = null;
    private boolean _necropsyComplete = false;
    private boolean _bleedOutComplete = false;
    private Mouse[] _mice = null;
    private Logger _log = Logger.getLogger(Cage.class);

    public Cage()
    {
    }

    public int getCageId()
    {
        return _cageId;
    }

    public void setCageId(int cageId)
    {
        _cageId = cageId;
    }

    public java.lang.String getCageName()
    {
        return _cageName;
    }

    public void setCageName(java.lang.String cageName)
    {
        _cageName = cageName;
    }

    public java.lang.String getContainer()
    {
        return _container;
    }

    public void setContainer(java.lang.String container)
    {
        _container = container;
    }

    public int getModelId()
    {
        return _modelId;
    }

    public void setModelId(int modelId)
    {
        _modelId = modelId;
    }

    public java.lang.String getSex()
    {
        return _sex;
    }

    public void setSex(java.lang.String sex)
    {
        _sex = sex;
    }

    public boolean getNecropsyComplete()
    {
        return _necropsyComplete;
    }

    public void setNecropsyComplete(boolean necropsyComplete)
    {
        _necropsyComplete = necropsyComplete;
    }

    public boolean getBleedOutComplete()
    {
        return _bleedOutComplete;
    }

    public void setBleedOutComplete(boolean bleedOutComplete)
    {
        _bleedOutComplete = bleedOutComplete;
    }

    public Mouse[] getMice()
    {

        if (null == _mice)
            try
            {
                DbSchema schema = MouseSchema.getSchema();
                Mouse[] mice = Table.select(schema.getTable("Mouse"), Table.ALL_COLUMNS, new SimpleFilter("cageId", new Integer(getCageId())), null, Mouse.class);
                setMice(mice);
            }
            catch (Exception x)
            {
                _log.error("getMice()", x);
            }

        return _mice;
    }

    public void setMice(Mouse[] mice)
    {
        _mice = mice;
    }

    public int getMouseCount()
    {
        int count = 0;

        if (null == _mice)
            return 0;

        for (int i = 0; i < _mice.length; i++)
            if (null != _mice[i] && null != _mice[i].getToeNo())
                count++;

        return count;
    }
}
