/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

import org.labkey.api.assay.nab.Luc5Assay;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.statistics.CurveFit;
import org.labkey.api.data.statistics.DoublePoint;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.WellData;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AbstractPlateBasedAssayProvider;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: brittp
 * Date: Jun 4, 2006
 * Time: 5:48:15 PM
 */
public class DilutionSummary implements Serializable
{
    private List<WellGroup> _sampleGroups;
    private WellGroup _firstGroup;
    private Map<StatsService.CurveFitType, DilutionCurve> _dilutionCurve = new HashMap<>() ;
    private Luc5Assay _assay;
    private String _lsid;
    private StatsService.CurveFitType _curveFitType;
    protected DilutionMaterialKey _materialKey = null;
    protected Container _container;
    public static final DilutionMaterialKey BLANK_NAB_MATERIAL = new DilutionMaterialKey(ContainerManager.getRoot(), "Blank", null, null, null, null);


    public DilutionSummary(Luc5Assay assay, WellGroup sampleGroup, String lsid, StatsService.CurveFitType curveFitType)
    {
        this(assay, Collections.singletonList(sampleGroup), lsid, curveFitType);
    }

    public DilutionSummary(Luc5Assay assay, List<WellGroup> sampleGroups, String lsid, StatsService.CurveFitType curveFitType)
    {
        assert sampleGroups != null && !sampleGroups.isEmpty() : "sampleGroups cannot be null or empty";
        assert assay != null : "assay cannot be null";
        ensureSameSample(sampleGroups);
        _curveFitType = curveFitType;
        _sampleGroups = sampleGroups;
        _firstGroup = sampleGroups.get(0);
        _assay = assay;
        _lsid = lsid;

        if (assay.getRunRowId() != null)
        {
            ExpRun run = ExperimentService.get().getExpRun(assay.getRunRowId());
            if (run != null)
                _container = run.getContainer();
        }
        else
        {
            // legacy nab assay instances do not use the run row id
            _container = ContainerManager.getRoot();
        }
    }

    private void ensureSameSample(List<WellGroup> groups)
    {
        String templateName = groups.get(0).getPlate().getName();
        String wellgroupName = groups.get(0).getName();
        for (int groupIndex = 1; groupIndex < groups.size(); groupIndex++)
        {
            if (!templateName.equals(groups.get(groupIndex).getPlate().getName()))
            {
                throw new IllegalStateException("Cannot generate single dilution summary for multiple plate templates: " +
                        templateName + ", " + groups.get(groupIndex).getPlate().getName());
            }

            if (!wellgroupName.equals(groups.get(groupIndex).getName()))
            {
                throw new IllegalStateException("Cannot generate single dilution summary for multiple samples: " +
                        wellgroupName + ", " + groups.get(groupIndex).getName());
            }
        }
    }

    public DilutionMaterialKey getMaterialKey()
    {
        if (_materialKey == null)
        {
            WellGroup firstWellGroup = getFirstWellGroup();
            String specimenId = (String) firstWellGroup.getProperty(AbstractAssayProvider.SPECIMENID_PROPERTY_NAME);
            Double visitId = (Double) firstWellGroup.getProperty(AbstractAssayProvider.VISITID_PROPERTY_NAME);
            String participantId = (String) firstWellGroup.getProperty(AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME);
            Date visitDate = (Date) firstWellGroup.getProperty(AbstractAssayProvider.DATE_PROPERTY_NAME);
            String virusName = null;
            if (_assay instanceof DilutionAssayRun)
            {
                String virusWellGroupName = (String) firstWellGroup.getProperty(AbstractPlateBasedAssayProvider.VIRUS_WELL_GROUP_NAME);
                if (null != virusWellGroupName)
                    virusName = ((DilutionAssayRun) _assay).getVirusName(virusWellGroupName);
            }
            _materialKey = new DilutionMaterialKey(_container, specimenId, participantId, visitId, visitDate, virusName);
        }
        return _materialKey;
    }

    public boolean isBlank()
    {
        return BLANK_NAB_MATERIAL.equals(getMaterialKey());
    }

    public double getDilution(WellData data)
    {
        return data.getDilution();
    }

    public double getCount(WellData data)
    {
        return data.getMean();
    }

    public double getStdDev(WellData data)
    {
        return data.getStdDev();
    }


    @Deprecated // used only by old NAb assay; always null for new NAb runs
    public String getSampleId()
    {
        return (String) _firstGroup.getProperty(SampleProperty.SampleId.name());
    }

    @Deprecated // used only by old NAb assay; always null for new NAb runs
    public String getSampleDescription()
    {
        return (String) _firstGroup.getProperty(SampleProperty.SampleDescription.name());
    }

    private Map<WellData, WellGroup> _dataToSample;
    private Map<WellData, WellGroup> getDataToSampleMap()
    {
        if (_dataToSample == null)
        {
            _dataToSample = new HashMap<>();
            for (WellGroup sampleGroup : _sampleGroups)
            {
                for (WellData data : sampleGroup.getWellData(true))
                {
                    _dataToSample.put(data, sampleGroup);
                }
            }
        }
        return _dataToSample;
    }

    public double getPercent(WellData data) throws FitFailedException
    {
        return _assay.getPercent(getDataToSampleMap().get(data), data);
    }

    private DilutionCurve getDilutionCurve(StatsService.CurveFitType type) throws FitFailedException
    {
        if (!_dilutionCurve.containsKey(type))
        {
            DilutionCurve curve = PlateService.get().getDilutionCurve(_sampleGroups, getMethod() == SampleInfoMethod.Dilution, _assay, type);
            _dilutionCurve.put(type, curve);
        }
        return _dilutionCurve.get(type);
    }

    public double getPlusMinus(WellData data) throws FitFailedException
    {
        if (getPercent(data) == 0)
            return 0;
        else
        {
            String virusWellGroupName = (String) getFirstWellGroup().getProperty(AbstractPlateBasedAssayProvider.VIRUS_WELL_GROUP_NAME);
            return getStdDev(data) / _assay.getControlRange(data.getPlate(), virusWellGroupName);
        }
    }

    public List<WellData> getWellData()
    {
        List<WellData> data = new ArrayList<>();
        for (WellGroup sampleGroup : _sampleGroups)
            data.addAll(sampleGroup.getWellData(true));
        return data;
    }

    public double getInitialDilution()
    {
        return (Double) _firstGroup.getProperty(SampleProperty.InitialDilution.name());
    }

    public double getFactor()
    {
        return (Double) _firstGroup.getProperty(SampleProperty.Factor.name());
    }

    public SampleInfoMethod getMethod()
    {
        String name = (String) _firstGroup.getProperty(SampleProperty.Method.name());
        return SampleInfoMethod.valueOf(name);
    }

    public double getCutoffDilution(double cutoff, StatsService.CurveFitType type) throws FitFailedException
    {
        return getDilutionCurve(type).getCutoffDilution(cutoff);
    }

    public double getInterpolatedCutoffDilution(double cutoff, StatsService.CurveFitType type) throws FitFailedException
    {
        return getDilutionCurve(type).getInterpolatedCutoffDilution(cutoff);
    }

    public DoublePoint[] getCurve() throws FitFailedException
    {
        return getDilutionCurve(_curveFitType).getCurve();
    }

    public double getFitError() throws FitFailedException
    {
        return getDilutionCurve(_curveFitType).getFitError();
    }

    public double getMinDilution(StatsService.CurveFitType type) throws FitFailedException
    {
        return getDilutionCurve(type).getMinDilution();
    }

    public double getMaxDilution(StatsService.CurveFitType type) throws FitFailedException
    {
        return getDilutionCurve(type).getMaxDilution();
    }

    public String getLSID()
    {
        return _lsid;
    }

    public Luc5Assay getAssay()
    {
        return _assay;
    }

    public List<WellGroup> getWellGroups()
    {
        return _sampleGroups;
    }

    public WellGroup getFirstWellGroup()
    {
        return _firstGroup;
    }

    public CurveFit.Parameters getCurveParameters(StatsService.CurveFitType type) throws FitFailedException
    {
        return getDilutionCurve(type).getParameters();
    }

    public double getAUC(StatsService.CurveFitType type, StatsService.AUCType calc) throws FitFailedException
    {
        return getDilutionCurve(type).calculateAUC(calc);
    }

    public double getAUC() throws FitFailedException
    {
        return getDilutionCurve(_assay.getRenderedCurveFitType()).calculateAUC(StatsService.AUCType.NORMAL);
    }

}
