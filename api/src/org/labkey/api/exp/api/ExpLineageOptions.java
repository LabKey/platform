/*
 * Copyright (c) 2016-2018 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;

/**
 * Captures options for doing a lineage search
 * Created by Nick Arnold on 2/12/2016.
 */
public class ExpLineageOptions extends ResolveLsidsForm
{
    public enum LineageExpType
    {
        ALL,
        Data,
        Material,
        ExperimentRun,
        Object;

        public static @Nullable LineageExpType fromValue(String value)
        {
            for (LineageExpType type : LineageExpType.values()) {
                if (type.name().equals(value)) {
                    return type;
                }
            }

            return null;
        }

    }

    private int _depth;
    private boolean _parents = true;
    private boolean _children = true;
    private LineageExpType _expType;
    private String _cpasType;
    private boolean _forLookup = false;
    private boolean _useObjectIds = false;
    private boolean _onlyReturnObjectId = false;
    private String _runProtocolLsid;

    public ExpLineageOptions()
    {
    }

    public ExpLineageOptions(boolean parents, boolean children, int depth)
    {
        _parents = parents;
        _children = children;
        _depth = depth;
    }

    public int getDepth()
    {
        return _depth;
    }

    public void setDepth(int depth)
    {
        _depth = depth;
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

    public String getExpTypeValue()
    {
        return _expType == null ? null : _expType.name();
    }

    public void setExpType(LineageExpType expType)
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

    public boolean isUseObjectIds()
    {
        return _useObjectIds;
    }

    /** user provides SQLFragment that selects objectids rather than lsids
     * TODO switch all usages to objectids
     */
    public void setUseObjectIds(boolean useObjectIds)
    {
        _useObjectIds = useObjectIds;
    }

    /**
     *  Use setOnlySelectObjectId() if you only care about the set of objectids returned form the lineage query.
     *
     * NOTE: that also means that there is no implicit container filter on the result.  The
     * edges table does not know about containers, and we are not joining to the experiments
     * tables to find a container.
     */
    public void setOnlySelectObjectId(boolean returnObjectId)
    {
        // obviously not interested in reconstructing the hierarchy, so use the lookup query
        _forLookup = true;
        _onlyReturnObjectId = returnObjectId;
    }

    public boolean isOnlySelectObjectId()
    {
        return _onlyReturnObjectId;
    }

    public String getRunProtocolLsid()
    {
        return _runProtocolLsid;
    }

    public void setRunProtocolLsid(String runProtocolLsid)
    {
        _runProtocolLsid = runProtocolLsid;
    }

}
