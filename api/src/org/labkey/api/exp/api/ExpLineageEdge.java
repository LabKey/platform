package org.labkey.api.exp.api;

import java.util.Objects;

public class ExpLineageEdge
{
    private Integer _fromObjectId;
    private Integer _runId;
    private Integer _sourceId;
    private String _sourceKey;
    private Integer _toObjectId;

    // Necessary for database serialization
    public ExpLineageEdge()
    {
    }

    public ExpLineageEdge(Integer fromObjectId, Integer toObjectId, Integer runId, Integer sourceId, String sourceKey)
    {
        _fromObjectId = fromObjectId;
        _toObjectId = toObjectId;
        _runId = runId;
        _sourceId = sourceId;
        _sourceKey = sourceKey;
    }

    public Integer getFromObjectId()
    {
        return _fromObjectId;
    }

    public void setFromObjectId(Integer fromObjectId)
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

    public Integer getToObjectId()
    {
        return _toObjectId;
    }

    public void setToObjectId(Integer toObjectId)
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

    public static class Options
    {
        public Integer fromObjectId;
        public Integer runId;
        public Integer sourceId;
        public String sourceKey;
        public Integer toObjectId;

        public Options fromObjectId(Integer fromObjectId)
        {
            this.fromObjectId = fromObjectId;
            return this;
        }

        public Options runId(Integer runId)
        {
            this.runId = runId;
            return this;
        }

        public Options sourceId(Integer sourceId)
        {
            this.sourceId = sourceId;
            return this;
        }

        public Options sourceKey(String sourceKey)
        {
            this.sourceKey = sourceKey;
            return this;
        }

        public Options toObjectId(Integer toObjectId)
        {
            this.toObjectId = toObjectId;
            return this;
        }
    }
}
