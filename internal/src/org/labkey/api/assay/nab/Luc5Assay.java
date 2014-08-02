/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
package org.labkey.api.assay.nab;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.dilution.DilutionManager;
import org.labkey.api.assay.dilution.DilutionCurve;
import org.labkey.api.assay.dilution.DilutionSummary;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.study.Plate;
import org.labkey.api.study.Position;
import org.labkey.api.study.WellData;
import org.labkey.api.study.WellGroup;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * User: migra
 * Date: Feb 10, 2006
 * Time: 2:15:41 PM
 */
public abstract class Luc5Assay implements Serializable, DilutionCurve.PercentCalculator
{
    private Integer _runRowId;
    private int[] _cutoffs;
    private Map<Integer, String> _cutoffFormats;
    private File _dataFile;
    protected StatsService.CurveFitType _renderedCurveFitType;
    private boolean _lockAxes;

    public Luc5Assay(Integer runRowId, int[] cutoffs, StatsService.CurveFitType renderCurveFitType)
    {
        _renderedCurveFitType = renderCurveFitType;
        _runRowId = runRowId;
        _cutoffs = cutoffs;
    }

    public Luc5Assay(int runRowId, List<Integer> cutoffs, StatsService.CurveFitType renderCurveFitType)
    {
        this(runRowId, toIntArray(cutoffs), renderCurveFitType);
    }

    private static int[] toIntArray(List<Integer> cutoffs)
    {
        int[] cutoffArray = new int[cutoffs.size()];
        for (int i = 0; i < cutoffs.size(); i++)
            cutoffArray[i] = cutoffs.get(i);
        return cutoffArray;
    }

    protected DilutionSummary[] getDilutionSumariesForWellGroups(List<? extends WellGroup> specimenGroups)
    {
        int sampleIndex = 0;
        DilutionSummary[] dilutionSummaries = new DilutionSummary[specimenGroups.size()];
        for (WellGroup specimenGroup : specimenGroups)
            dilutionSummaries[sampleIndex++] = new DilutionSummary(this, specimenGroup, null, _renderedCurveFitType);
        return dilutionSummaries;
    }

    public abstract String getRunName();

    public abstract DilutionSummary[] getSummaries();

    public Integer getRunRowId()
    {
        return _runRowId;
    }

    public static String intString(double d)
    {
        return String.valueOf((int) Math.round(d));
    }

    public static String percentString(double d)
    {
        return intString(d * 100) + "%";
    }

    public int[] getCutoffs()
    {
        return _cutoffs;
    }

    public double getPercent(WellGroup group, WellData data) throws FitFailedException
    {
        if (null == group)
            throw new FitFailedException("Invalid well group.");
        Plate plate = group.getPlate();
        List<Position> dataPositions = (data instanceof WellGroup) ? ((WellGroup)data).getPositions() : null;
        WellData cellControl = getCellControlWells(plate, dataPositions);
        if (cellControl == null)
            throw new FitFailedException("Invalid plate template: no cell control well group was found.");
        WellData virusControl = getVirusControlWells(plate, dataPositions);
        if (virusControl == null)
            throw new FitFailedException("Invalid plate template: no virus control well group was found.");
        double controlRange = virusControl.getMean() - cellControl.getMean();
        double cellControlMean = cellControl.getMean();
        if (data.getMean() < cellControlMean)
            return 1.0;
        else
            return 1 - (data.getMean() - cellControlMean) / controlRange;
    }

    @Nullable
    @Override
    public WellGroup getCellControlWells(Plate plate, @Nullable List<Position> dataPositions)
    {
        return plate.getWellGroup(WellGroup.Type.CONTROL, DilutionManager.CELL_CONTROL_SAMPLE);
    }

    @Nullable
    @Override
    public WellGroup getVirusControlWells(Plate plate, @Nullable List<Position> dataPositions)
    {
        return plate.getWellGroup(WellGroup.Type.CONTROL, DilutionManager.VIRUS_CONTROL_SAMPLE);
    }

    public abstract List<Plate> getPlates();

    public double getControlRange(Plate plate, String virusWellGroupName)    // override to support multi-virus
    {
        WellData cellControl = plate.getWellGroup(WellGroup.Type.CONTROL, DilutionManager.CELL_CONTROL_SAMPLE);
        WellData virusControl = plate.getWellGroup(WellGroup.Type.CONTROL, DilutionManager.VIRUS_CONTROL_SAMPLE);
        return virusControl.getMean() - cellControl.getMean();
    }

    public double getVirusControlMean(Plate plate, String virusWellGroupName)
    {
        WellData virusControl = plate.getWellGroup(WellGroup.Type.CONTROL, DilutionManager.VIRUS_CONTROL_SAMPLE);
        return virusControl.getMean();
    }

    public double getCellControlMean(Plate plate, String virusWellGroupName)
    {
        WellData cellControl = plate.getWellGroup(WellGroup.Type.CONTROL, DilutionManager.CELL_CONTROL_SAMPLE);
        return cellControl.getMean();
    }

    public double getVirusControlPlusMinus(Plate plate, String virusWellGroupName)
    {
        WellData virusControl = plate.getWellGroup(WellGroup.Type.CONTROL, DilutionManager.VIRUS_CONTROL_SAMPLE);
        double virusControlMean = virusControl.getMean();
        double virusControlStdDev = virusControl.getStdDev();
        return virusControlStdDev / virusControlMean;
    }

    public double getCellControlPlusMinus(Plate plate, String virusWellGroupName)
    {
        WellData cellControl = plate.getWellGroup(WellGroup.Type.CONTROL, DilutionManager.CELL_CONTROL_SAMPLE);
        double cellControlMean = cellControl.getMean();
        double cellControlStdDev = cellControl.getStdDev();
        return cellControlStdDev / cellControlMean;
    }

    public WellGroup getCellControlWellGroup(Plate plate, String virusWellGroupName)
    {
        return plate.getWellGroup(WellGroup.Type.CONTROL, DilutionManager.CELL_CONTROL_SAMPLE);
    }

    public WellGroup getVirusControlWellGroup(Plate plate, String virusWellGroupName)
    {
        return plate.getWellGroup(WellGroup.Type.CONTROL, DilutionManager.CELL_CONTROL_SAMPLE);
    }

    public Map<Integer, String> getCutoffFormats()
    {
        return _cutoffFormats;
    }

    public void setCutoffFormats(Map<Integer, String> cutoffFormats)
    {
        _cutoffFormats = cutoffFormats;
    }

    public File getDataFile()
    {
        return _dataFile;
    }

    public void setDataFile(File dataFile)
    {
        _dataFile = dataFile;
    }

    public boolean isLockAxes()
    {
        return _lockAxes;
    }

    public void setLockAxes(boolean lockAxes)
    {
        _lockAxes = lockAxes;
    }

    public StatsService.CurveFitType getRenderedCurveFitType()
    {
        return _renderedCurveFitType;
    }
}
