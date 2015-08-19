package org.labkey.api.assay.dilution;

/**
 * Created by davebradlee on 8/14/15.
 *
 */
public class DilutionDataRow
{
    private int _rowId;
    private int _runId;
    private Integer _runDataId;
    private String _wellgroupName;
    private String _replicateName;
    private Integer _dilutionOrder;
    private Double _dilution;
    private Double _min;
    private Double _max;
    private Double _mean;
    private Double _stddev;
    private Double _minDilution;
    private Double _maxDilution;
    private int _plateNumber;
    private String _container;

    public DilutionDataRow()
    {
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public int getRunId()
    {
        return _runId;
    }

    public void setRunId(int runId)
    {
        _runId = runId;
    }

    public Integer getRunDataId()
    {
        return _runDataId;
    }

    public void setRunDataId(Integer runDataId)
    {
        _runDataId = runDataId;
    }

    public String getWellgroupName()
    {
        return _wellgroupName;
    }

    public void setWellgroupName(String wellgroupName)
    {
        _wellgroupName = wellgroupName;
    }

    public String getReplicateName()
    {
        return _replicateName;
    }

    public void setReplicateName(String replicateName)
    {
        _replicateName = replicateName;
    }

    public Integer getDilutionOrder()
    {
        return _dilutionOrder;
    }

    public void setDilutionOrder(Integer dilutionOrder)
    {
        this._dilutionOrder = dilutionOrder;
    }

    public Double getDilution()
    {
        return _dilution;
    }

    public void setDilution(Double dilution)
    {
        this._dilution = dilution;
    }

    public Double getMin()
    {
        return _min;
    }

    public void setMin(Double min)
    {
        this._min = min;
    }

    public Double getMax()
    {
        return _max;
    }

    public void setMax(Double max)
    {
        this._max = max;
    }

    public Double getMean()
    {
        return _mean;
    }

    public void setMean(Double mean)
    {
        this._mean = mean;
    }

    public Double getStddev()
    {
        return _stddev;
    }

    public void setStddev(Double stddev)
    {
        this._stddev = stddev;
    }

    public Double getMinDilution()
    {
        return _minDilution;
    }

    public void setMinDilution(Double minDilution)
    {
        this._minDilution = minDilution;
    }

    public Double getMaxDilution()
    {
        return _maxDilution;
    }

    public void setMaxDilution(Double maxDilution)
    {
        this._maxDilution = maxDilution;
    }

    public int getPlateNumber()
    {
        return _plateNumber;
    }

    public void setPlateNumber(int plateNumber)
    {
        this._plateNumber = plateNumber;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }
}
