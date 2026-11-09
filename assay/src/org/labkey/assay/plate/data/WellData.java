/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.assay.plate.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.query.WellTable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WellData
{
    private Integer _col;
    private String _lsid;
    private Map<String, Object> _metadata;
    private String _position;
    private String _replicateGroup;
    private Integer _row;
    private Long _rowId;
    private Long _sampleId;
    private WellGroup.Type _type;
    private String _wellGroup;
    // NOTE: If/when adding additional properties update hasData() to include the new properties

    public WellData()
    {
    }

    public Map<String, Object> getData()
    {
        return getData(false);
    }

    public Map<String, Object> getData(boolean includeWellId)
    {
        Map<String, Object> data = new CaseInsensitiveHashMap<>();
        if (includeWellId && _rowId != null)
            data.put(WellTable.Column.RowId.name(), _rowId);
        if (_position != null)
            data.put(WellTable.WELL_LOCATION, _position);
        if (_sampleId != null)
            data.put(WellTable.Column.SampleID.name(), _sampleId);
        if (_type != null)
            data.put(WellTable.Column.Type.name(), _type.name());
        if (_wellGroup != null)
            data.put(WellTable.Column.WellGroup.name(), _wellGroup);
        if (_replicateGroup != null)
            data.put(WellTable.Column.ReplicateGroup.name(), _replicateGroup);

        for (var entry : getMetadata().entrySet())
        {
            if (entry.getValue() != null)
                data.put(entry.getKey(), entry.getValue());
        }

        return data;
    }

    public boolean hasData()
    {
        // _position is not used when determining if the well data has data
        return _sampleId != null || _type != null && _wellGroup != null || !getMetadata().isEmpty();
    }

    public boolean isReplicate()
    {
        return _replicateGroup != null;
    }

    public boolean isSample()
    {
        return WellGroup.Type.SAMPLE.equals(getType());
    }

    public boolean isSampleOrReplicate()
    {
        return isSample() || isReplicate();
    }

    public Integer getCol()
    {
        return _col;
    }

    public void setCol(Integer col)
    {
        _col = col;
    }

    public @Nullable Pair<WellGroup.Type, String> getGroupKey()
    {
        if (isSample() || isReplicate())
        {
            if (_wellGroup != null)
                return Pair.of(WellGroup.Type.SAMPLE, _wellGroup);

            if (isReplicate())
                return Pair.of(WellGroup.Type.REPLICATE, _replicateGroup);
        }

        return null;
    }

    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        _lsid = lsid;
    }

    public Map<String, Object> getMetadata()
    {
        return _metadata == null ? Collections.emptyMap() : _metadata;
    }

    public void setMetadata(Map<String, Object> metadata)
    {
        _metadata = metadata;
    }

    public String getPosition()
    {
        return _position;
    }

    public void setPosition(String position)
    {
        _position = position;
    }

    public String getReplicateGroup()
    {
        return _replicateGroup;
    }

    public void setReplicateGroup(String replicateGroup)
    {
        _replicateGroup = replicateGroup;
    }

    public Integer getRow()
    {
        return _row;
    }

    public void setRow(Integer row)
    {
        _row = row;
    }

    public Long getRowId()
    {
        return _rowId;
    }

    public void setRowId(Long rowId)
    {
        _rowId = rowId;
    }

    public Long getSampleId()
    {
        return _sampleId;
    }

    public void setSampleId(Long sampleId)
    {
        _sampleId = sampleId;
    }

    public WellGroup.Type getType()
    {
        return _type;
    }

    public void setType(WellGroup.Type type)
    {
        _type = type;
    }

    public String getWellGroup()
    {
        return _wellGroup;
    }

    public void setWellGroup(String wellGroup)
    {
        _wellGroup = wellGroup;
    }

    record CacheKey(long plateRowId, boolean includeSamples, boolean includeMetadata) {}

    public static class Cache
    {
        private final Map<CacheKey, List<WellData>> cache;
        private final Container container;
        private final User user;

        public Cache(Container container, User user)
        {
            cache = new HashMap<>();
            this.container = container;
            this.user = user;
        }

        public List<WellData> getData(long plateRowId, boolean includeSamples, boolean includeMetadata)
        {
            return cache.computeIfAbsent(
                new CacheKey(plateRowId, includeSamples, includeMetadata),
                (k) -> PlateManager.get().getWellData(container, user, k.plateRowId, k.includeSamples, k.includeMetadata)
            );
        }
    }
}
