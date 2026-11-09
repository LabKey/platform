/*
 * Copyright (c) 2022-2026 LabKey Corporation
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

import org.labkey.api.collections.LongHashSet;

import java.util.Objects;
import java.util.Set;

public class ExpLineageEdge
{
    private long _fromObjectId; // not nullable
    private Long _runId;
    private Long _sourceId;
    private String _sourceKey;
    private long _toObjectId; // not nullable

    // Necessary for database serialization
    public ExpLineageEdge()
    {
    }

    public ExpLineageEdge(long fromObjectId, long toObjectId, Long runId, Long sourceId, String sourceKey)
    {
        _fromObjectId = fromObjectId;
        _toObjectId = toObjectId;
        _runId = runId == null ? null : runId.longValue();
        _sourceId = sourceId == null ? null : sourceId.longValue();
        _sourceKey = sourceKey;
    }

    public long getFromObjectId()
    {
        return _fromObjectId;
    }

    public void setFromObjectId(long fromObjectId)
    {
        _fromObjectId = fromObjectId;
    }

    public Long getRunId()
    {
        return _runId;
    }

    public void setRunId(Long runId)
    {
        _runId = runId;
    }

    public Long getSourceId()
    {
        return _sourceId;
    }

    public void setSourceId(Long sourceId)
    {
        _sourceId = sourceId;
    }

    public String getSourceKey()
    {
        return _sourceKey;
    }

    public void setSourceKey(String sourceKey)
    {
        _sourceKey = sourceKey;
    }

    public long getToObjectId()
    {
        return _toObjectId;
    }

    public void setToObjectId(long toObjectId)
    {
        _toObjectId = toObjectId;
    }

    @Override
    public String toString()
    {
        return String.format(
            "fromObjectId: %d, toObjectId: %d, runId: %d, sourceId: %d, sourceKey: %s",
            _fromObjectId, _toObjectId, _runId, _sourceId, _sourceKey
        );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(_fromObjectId, _toObjectId, _runId, _sourceId, _sourceKey);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof ExpLineageEdge edge))
            return false;

        return (
            Objects.equals(_fromObjectId, edge.getFromObjectId()) &&
            Objects.equals(_toObjectId, edge.getToObjectId()) &&
            Objects.equals(_runId, edge.getRunId()) &&
            Objects.equals(_sourceId, edge.getSourceId()) &&
            Objects.equals(_sourceKey, edge.getSourceKey())
        );
    }

    public static class FilterOptions
    {
        public Long fromObjectId;
        public Long runId;
        public Set<Long> sourceIds;
        public String sourceKey;
        public Long toObjectId;

        public FilterOptions fromObjectId(Long fromObjectId)
        {
            // TODO BIGINT
            this.fromObjectId = fromObjectId;
            return this;
        }

        public FilterOptions runId(Long runId)
        {
            this.runId = runId;
            return this;
        }

        public FilterOptions sourceId(Long sourceId)
        {
            this.sourceIds = Set.of(sourceId);
            return this;
        }

        public FilterOptions sourceIds(Set<Long> sourceIds)
        {
            this.sourceIds = new LongHashSet(sourceIds);
            return this;
        }

        public FilterOptions sourceKey(String sourceKey)
        {
            this.sourceKey = sourceKey;
            return this;
        }

        public FilterOptions toObjectId(Long toObjectId)
        {
            this.toObjectId = toObjectId;
            return this;
        }
    }
}
