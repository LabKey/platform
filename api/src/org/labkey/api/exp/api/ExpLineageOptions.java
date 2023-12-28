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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.ModuleLoader;

/**
 * Captures options for doing a lineage search
 */
public class ExpLineageOptions extends ResolveLsidsForm
{
    // Issue 37332: SQL Server can hit max recursion depth over 100 generations.
    // Note: If this is adjusted higher, then consider separate default values for SQL Server and PostgreSQL.
    public static final int LINEAGE_DEFAULT_MAXIMUM_DEPTH = 100;

    public enum LineageExpType
    {
        ALL,
        Data,
        Material,
        ExperimentRun,
        Object;

        public static @Nullable LineageExpType fromValue(String value)
        {
            for (LineageExpType type : LineageExpType.values())
            {
                if (type.name().equals(value))
                    return type;
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
    private String _sourceKey;

    public ExpLineageOptions()
    {
    }

    public ExpLineageOptions(boolean parents, boolean children, int depth)
    {
        _parents = parents;
        _children = children;
        _depth = depth;
    }

    public String getExpEdge()
    {
        return "exp.Edge";
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

    public @Nullable String getSourceKey()
    {
        return StringUtils.trimToNull(_sourceKey);
    }

    public void setSourceKey(String sourceKey)
    {
        _sourceKey = sourceKey;
    }

    public int getConfiguredDepth()
    {
        if (_depth != 0)
            return Math.abs(_depth);

        var module = ModuleLoader.getInstance().getModule(ExperimentService.MODULE_NAME);
        var property = module.getModuleProperties().get(ExperimentService.LINEAGE_DEFAULT_MAXIMUM_DEPTH_PROPERTY_NAME);
        if (property != null)
        {
            String sDepth = property.getEffectiveValue(null);
            if (!StringUtils.isEmpty(sDepth))
                return Integer.parseInt(sDepth);
        }

        return LINEAGE_DEFAULT_MAXIMUM_DEPTH;
    }
}
