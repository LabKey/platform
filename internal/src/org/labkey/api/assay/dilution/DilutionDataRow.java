/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.api.assay.dilution;

import java.util.Map;

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

    public static DilutionDataRow fromMap(Map<String, Object> data)
    {
        DilutionDataRow row = new DilutionDataRow();

        if (data.containsKey("rowId"))
            row.setRowId((Integer)data.get("rowId"));
        if (data.containsKey("runId"))
            row.setRunId((Integer)data.get("runId"));
        if (data.containsKey("runDataId"))
            row.setRunDataId((Integer)data.get("runDataId"));
        if (data.containsKey("wellGroupName"))
            row.setWellgroupName(String.valueOf(data.get("wellGroupName")));
        if (data.containsKey("replicateName"))
            row.setReplicateName(String.valueOf(data.get("replicateName")));
        if (data.containsKey("dilutionOrder"))
            row.setDilutionOrder((Integer)data.get("dilutionOrder"));
        if (data.containsKey("dilution"))
            row.setDilution((Double)data.get("dilution"));
        if (data.containsKey("min"))
            row.setMin((Double)data.get("min"));
        if (data.containsKey("max"))
            row.setMax((Double)data.get("max"));
        if (data.containsKey("mean"))
            row.setMean((Double)data.get("mean"));
        if (data.containsKey("stdDev"))
            row.setStddev((Double)data.get("stdDev"));
        if (data.containsKey("minDilution"))
            row.setMinDilution((Double)data.get("minDilution"));
        if (data.containsKey("maxDilution"))
            row.setMaxDilution((Double)data.get("maxDilution"));
        if (data.containsKey("plateNumber"))
            row.setPlateNumber((Integer)data.get("plateNumber"));
        if (data.containsKey("container"))
            row.setContainer(String.valueOf(data.get("container")));

        return row;
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
