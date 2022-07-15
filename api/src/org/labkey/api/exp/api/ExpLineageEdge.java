package org.labkey.api.exp.api;

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
