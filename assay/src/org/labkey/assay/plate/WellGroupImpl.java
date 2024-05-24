/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.assay.plate;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.dilution.DilutionCurve;
import org.labkey.api.assay.dilution.DilutionDataRow;
import org.labkey.api.assay.dilution.DilutionManager;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.Well;
import org.labkey.api.assay.plate.WellData;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WellGroupImpl extends PropertySetImpl implements WellGroup
{
    private PlateImpl _plate;
    private Double _mean = null;
    private Double _stdDev = null;
    private Double _min;
    private Double _max;
    private Set<WellGroup> _overlappingGroups;
    private List<WellData> _replicateWellData;
    private DilutionDataRow _dilutionDataRow = null;

    private Integer _rowId;
    private String _name;
    private WellGroup.Type _type;
    boolean _deleted;

    protected Integer _plateId;
    protected List<? extends Position> _positions;

    public WellGroupImpl()
    {
        // no-param constructor for reflection
    }

    public WellGroupImpl(PlateImpl owner, String name, WellGroup.Type type, List<? extends Position> positions)
    {
        super(owner.getContainer());
        _type = type;
        _name = name;
        _plateId = owner.getRowId() != null ? owner.getRowId() : null;
        _plate = owner;
        _positions = sortPositions(positions);
    }

    public WellGroupImpl(PlateImpl plate, WellGroupImpl template)
    {
        this(plate, template.getName(), template.getType(), template.getPositions());
        _plate = plate;
        for (Map.Entry<String, Object> entry : template.getProperties().entrySet())
            setProperty(entry.getKey(), entry.getValue());
    }

    @Override
    public @Nullable ActionURL detailsURL()
    {
        if (_plateId == null)
            return null;

        Plate template = PlateService.get().getPlate(getContainer(), _plateId);
        if (template == null)
            return null;

        return template.detailsURL();
    }

    private static List<? extends Position> sortPositions(List<? extends Position> positions)
    {
        List<? extends Position> sortedPositions = new ArrayList<>(positions);
        sortedPositions.sort(Comparator.comparingInt(Position::getColumn).thenComparingInt(Position::getRow));
        return sortedPositions;
    }

    @Override
    public List<Position> getPositions()
    {
        return Collections.unmodifiableList(_positions);
    }

    @Override
    public void setPositions(List<? extends Position> positions)
    {
        _positions = sortPositions(positions);
    }

    @Override
    public WellGroup.Type getType()
    {
        return _type;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    @Override
    public boolean contains(Position position)
    {
        return _positions.contains(position);
    }

    @Override
    public String getPositionDescription()
    {
        if (_positions == null || _positions.size() == 0)
            return "";
        if (_positions.size() == 1)
            return _positions.get(0).getDescription();
        return _positions.get(0).getDescription() + "-" + _positions.get(_positions.size() - 1).getDescription();
    }

    @Override
    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public String getTypeName()
    {
        return _type.name();
    }

    public void setTypeName(String type)
    {
        _type = WellGroup.Type.valueOf(type);
    }

    public Integer getPlateId()
    {
        return _plateId;
    }

    public void setPlateId(Integer plateId)
    {
        _plateId = plateId;
    }

    public boolean isTemplate()
    {
        return _plate != null ? _plate.isTemplate() : false;
    }

    public Position getTopLeft()
    {
        if (_positions.isEmpty())
            return null;
        return _positions.get(0);
    }

    /**
     * Mark the well group as deleted.
     * @see PlateImpl#markWellGroupForDeletion(WellGroup)
     */
    public void delete()
    {
        _deleted = true;
    }

    @Override
    public synchronized Set<WellGroup> getOverlappingGroups()
    {
        if (_overlappingGroups == null)
        {
            _overlappingGroups = new LinkedHashSet<>();
            for (Position position : getPositions())
            {
                List<WellGroup> groups = _plate.getWellGroups(position);
                for (WellGroup group : groups)
                {
                    if (group != this)
                        _overlappingGroups.add(group);
                }
            }
        }
        return _overlappingGroups;
    }

    @Override
    public Set<WellGroup> getOverlappingGroups(Type type)
    {
        Set<WellGroup> typedGroups = new LinkedHashSet<>();
        for (WellGroup overlappingGroup : getOverlappingGroups())
        {
            if (type == overlappingGroup.getType())
                typedGroups.add(overlappingGroup);
        }
        return typedGroups;
    }

    @Override
    public synchronized List<? extends WellData> getWellData(boolean combineReplicates)
    {
        if (!combineReplicates)
        {
            List<WellData> returnList = new ArrayList<>(_positions.size());
            for (Position position : _positions)
                returnList.add(_plate.getWell(position.getRow(), position.getColumn()));
            return returnList;
        }
        else
        {
            if (_replicateWellData == null)
            {
                _replicateWellData = new ArrayList<>();
                // first, we find all replicate groups that overlap with this group:
                Set<WellGroup> replicateGroups = getOverlappingGroups(Type.REPLICATE);
                // create a mapping from each position to its replicate group, if one exists:
                Map<Position, WellGroup> positionToReplicateGroup = new HashMap<>();
                for (WellGroup replicateGroup : replicateGroups)
                {
                    for (Position position : replicateGroup.getPositions())
                        positionToReplicateGroup.put(position, replicateGroup);
                }

                Set<WellGroup> addedReplicateGroups = new HashSet<>();
                // go through each well in order, adding the well to our data list if there is no
                // associated replicate group, or adding the replicate group if that position's
                // group hasn't been added before:
                for (Position position : _positions)
                {
                    WellGroup replicateGroup = positionToReplicateGroup.get(position);
                    if (replicateGroup != null)
                    {
                        if (!addedReplicateGroups.contains(replicateGroup))
                        {
                            _replicateWellData.add(replicateGroup);
                            addedReplicateGroups.add(replicateGroup);
                        }
                    }
                    else
                        _replicateWellData.add(_plate.getWell(position.getRow(), position.getColumn()));
                }
            }
            return _replicateWellData;
        }
    }

    @Override
    public double getStdDev()
    {
        computeStats();
        return _stdDev;
    }

    @Override
    public double getMax()
    {
        computeStats();
        return _max;
    }

    @Override
    public double getMin()
    {
        computeStats();
        return _min;
    }

    @Override
    public double getMean()
    {
        computeStats();
        return _mean;
    }

    @Override
    public Plate getPlate()
    {
        if (_plate == null && _plateId != null)
        {
            _plate = (PlateImpl) PlateCache.getPlate(getContainer(), _plateId);
        }
        return _plate;
    }

    @Override
    public Double getDilution()
    {
        if (null != _dilutionDataRow)
            return _dilutionDataRow.getDilution();

        Double dilution = null;
        for (Position position: _positions)
        {
            Double current = _plate.getWell(position.getRow(), position.getColumn()).getDilution();
            if (dilution == null)
                dilution = current;
            else if (!dilution.equals(current))
                return null;
        }
        return dilution;
    }

    @Override
    public void setDilution(Double dilution)
    {
        for (Position position : _positions)
            _plate.getWell(position.getRow(), position.getColumn()).setDilution(dilution);
    }

    public DilutionCurve getDilutionCurve(DilutionCurve.PercentCalculator calculator, boolean expectedDecreasing, StatsService.CurveFitType fitType) throws FitFailedException
    {
        return CurveFitFactory.getCurveImpl(this, expectedDecreasing, calculator, fitType);
    }

    @Override
    public Double getMaxDilution()
    {
        if (null != _dilutionDataRow)
            return _dilutionDataRow.getMaxDilution();

        List<? extends WellData> datas = getWellData(true);
        Double max = null;
        for (WellData data : datas)
        {
            Double nextDilution = data.getDilution();
            if (max == null || (nextDilution != null && max < nextDilution))
                max = nextDilution;
        }
        return max;
    }

    @Override
    public Double getMinDilution()
    {
        if (null != _dilutionDataRow)
            return _dilutionDataRow.getMinDilution();

        List<? extends WellData> datas = getWellData(true);
        Double min = null;
        for (WellData data : datas)
        {
            Double nextDilution = data.getDilution();
            if (min == null || (nextDilution != null && min > nextDilution))
                min = nextDilution;
        }
        return min;
    }

    public void setPlate(PlateImpl plate)
    {
        _plate = plate;
    }

    private synchronized void computeStats()
    {
        if (null == _stdDev)
        {
            if (_plate.mustCalculateStats())
                recomputeStats();
            else
                populateStatsFromTable();
        }
    }

    private synchronized void recomputeStats()
    {
        List<Double> values = new ArrayList<>();
        for (WellData data : getWellData(true))
        {
            if (data instanceof Well && ((Well)data).isExcluded())
                continue;
            values.add(data.getMean());
        }
        computeStats(values.toArray(new Double[values.size()]));
    }

    private synchronized void computeStats(Double[] data)
    {
        // sd is sqrt of sum of (values-mean) squared divided by n - 1
        // Calculate the mean
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        if (null == data || data.length == 0)
        {
            _mean = Double.NaN;
            _stdDev = Double.NaN;
            _min = Double.NaN;
            _max = Double.NaN;
            return;
        }

        final int n = data.length;
        if (data.length == 1)
        {
            _mean = data[0];
            _stdDev = Double.NaN;
            _min = data[0];
            _max = data[0];
            return;
        }

        _mean = 0.0;
        for (int i = 0; i < n; i++)
        {
            double d = data[i];
            _mean += d;
            if (d < min)
                min = d;
            if (d > max)
                max = d;
        }
        _mean /= n;
        _min = min;
        _max = max;

        // calculate the sum of squares
        double sum = 0;
        for (int i = 0; i < n; i++)
        {
            final double v = data[i] - _mean;
            sum += v * v;
        }
        _stdDev = Math.sqrt(sum / (n - 1));
    }

    private void populateStatsFromTable()
    {
        ExpRun run = ExperimentService.get().getExpRun(_plate.getRunId());
        List<DilutionDataRow> dilutionDataRows = DilutionManager.getDilutionDataRows(_plate.getRunId(),
                _plate.getPlateNumber(), getName(), run.getContainer(),
                Type.REPLICATE.equals(getType()));
        if (1 != dilutionDataRows.size())
            throw new IllegalStateException("Expected a single DilutionData row to calculate wellgroup stats, but found " + dilutionDataRows.size() + " rows");
        _dilutionDataRow = dilutionDataRows.get(0);
        _mean = _dilutionDataRow.getMean();
        _min = _dilutionDataRow.getMin();
        _max = _dilutionDataRow.getMax();
        _stdDev = _dilutionDataRow.getStddev();
    }
}
