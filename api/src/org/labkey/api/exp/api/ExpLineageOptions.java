/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.api.exp.api;

import org.labkey.api.settings.AppProps;

/**
 * Created by Nick Arnold on 2/12/2016.
 */
public class ExpLineageOptions
{
    private boolean _veryNewHotness = AppProps.getInstance().isExperimentalFeatureEnabled(ExperimentService.EXPERIMENTAL_LINEAGE_PERFORMANCE);
    private int _rowId;
    private String _lsid;
    private int _depth;
    private boolean _parents = true;
    private boolean _children = true;
    private String _expType;
    private String _cpasType;
    private boolean _forLookup = false;

    public boolean isVeryNewHotness()
    {
        return _veryNewHotness;
    }

    public void setVeryNewHotness(boolean b)
    {
        _veryNewHotness = b;
    }

    public int getDepth()
    {
        return _depth;
    }

    public void setDepth(int depth)
    {
        _depth = depth;
    }

    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        _lsid = lsid;
    }

    public String getLSID()
    {
        return getLsid();
    }

    public void setLSID(String lsid)
    {
        setLsid(lsid);
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public boolean isParents()
    {
        return _parents;
    }

    public void setParents(boolean parents)
    {
        _parents = parents;
    }

    public boolean isChildren()
    {
        return _children;
    }

    public void setChildren(boolean children)
    {
        _children = children;
    }

    public String getExpType()
    {
        return _expType;
    }

    public void setExpType(String expType)
    {
        _expType = expType;
    }

    public String getCpasType()
    {
        return _cpasType;
    }

    public void setCpasType(String cpasType)
    {
        _cpasType = cpasType;
    }

    public boolean isForLookup()
    {
        return _forLookup;
    }

    public void setForLookup(boolean b)
    {
        _forLookup = b;
    }
}
