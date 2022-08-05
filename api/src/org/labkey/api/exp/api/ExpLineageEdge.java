package org.labkey.api.exp.api;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ExpLineageEdge
{
    private int _fromObjectId; // not nullable
    private Integer _runId;
    private Integer _sourceId;
    private String _sourceKey;
    private int _toObjectId; // not nullable

    // Necessary for database serialization
    public ExpLineageEdge()
    {
    }

    public ExpLineageEdge(int fromObjectId, int toObjectId, Integer runId, Integer sourceId, String sourceKey)
    {
        _fromObjectId = fromObjectId;
        _toObjectId = toObjectId;
        _runId = runId;
        _sourceId = sourceId;
        _sourceKey = sourceKey;
    }

    public int getFromObjectId()
    {
        return _fromObjectId;
    }

    public void setFromObjectId(int fromObjectId)
    {
        _fromObjectId = fromObjectId;
    }

    public Integer getRunId()
    {
        return _runId;
    }

    public void setRunId(Integer runId)
    {
        _runId = runId;
    }

    public Integer getSourceId()
    {
        return _sourceId;
    }

    public void setSourceId(Integer sourceId)
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

    public int getToObjectId()
    {
        return _toObjectId;
    }

    public void setToObjectId(int toObjectId)
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
        public Integer fromObjectId;
        public Integer runId;
        public Set<Integer> sourceIds;
        public String sourceKey;
        public Integer toObjectId;

        public FilterOptions fromObjectId(Integer fromObjectId)
        {
            this.fromObjectId = fromObjectId;
            return this;
        }

        public FilterOptions runId(Integer runId)
        {
            this.runId = runId;
            return this;
        }

        public FilterOptions sourceId(Integer sourceId)
        {
            this.sourceIds = Set.of(sourceId);
            return this;
        }

        public FilterOptions sourceIds(Set<Integer> sourceIds)
        {
            this.sourceIds = new HashSet<>(sourceIds);
            return this;
        }

        public FilterOptions sourceKey(String sourceKey)
        {
            this.sourceKey = sourceKey;
            return this;
        }

        public FilterOptions toObjectId(Integer toObjectId)
        {
            this.toObjectId = toObjectId;
            return this;
        }
    }
}
