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

package org.labkey.experiment.xar;

import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.experiment.ArchiveURLRewriter;
import org.labkey.experiment.URLRewriter;
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: jeckels
 * Date: Oct 31, 2007
 */
public class XarExportSelection implements Serializable
{
    // Store ids instead of experiment objects so that it's easily serializable
    private final List<Integer> _expIds = new ArrayList<>();
    private final Set<Integer> _runIds = new LinkedHashSet<>();
    private final List<Integer> _dataIds = new ArrayList<>();
    private final List<Integer> _sampleTypeIds = new ArrayList<>();
    private final List<Integer> _protocolIds = new ArrayList<>();
    private final List<Integer> _dataClassIds = new ArrayList<>();

    private boolean _includeXarXml = true;
    private Set<String> _roles;

    public void addExperimentIds(int... expIds)
    {
        for (int expId : expIds)
        {
            _expIds.add(expId);
        }
    }

    public void addRuns(Collection<? extends ExpRun> runs)
    {
        _runIds.addAll(runs.stream().map(ExpObject::getRowId).collect(Collectors.toSet()));
    }

    public void addDataIds(int... dataIds)
    {
        for (int dataId : dataIds)
        {
            _dataIds.add(dataId);
        }
    }

    public void addProtocolIds(int... protocolIds)
    {
        for (int protocolId : protocolIds)
        {
            _protocolIds.add(protocolId);
        }
    }

    public void addProtocolIds(Collection<Integer> protocolIds)
    {
        _protocolIds.addAll(protocolIds);
    }

    public boolean isIncludeXarXml()
    {
        return _includeXarXml;
    }

    public void setIncludeXarXml(boolean includeXarXml)
    {
        _includeXarXml = includeXarXml;
    }

    public void addRoles(String... roles)
    {
        if (_roles == null)
        {
            _roles = new HashSet<>();
        }
        _roles.addAll(Arrays.asList(roles));
    }

    public void addRoles(Set<String> roles)
    {
        if (_roles == null)
        {
            _roles = new HashSet<>();
        }
        _roles.addAll(roles);
    }

    public void addContent(XarExporter exporter) throws ExperimentException
    {
        for (int expId : _expIds)
        {
            exporter.addExperiment(ExperimentServiceImpl.get().getExpExperiment(expId));
        }

        for (int runId : _runIds)
        {
            exporter.addExperimentRun(ExperimentService.get().getExpRun(runId));
        }

        for (int protocolId : _protocolIds)
        {
            ExpProtocol protocol = ExperimentService.get().getExpProtocol(protocolId);
            exporter.addProtocol(protocol, true);
        }

        for (int sampleTypeId : _sampleTypeIds)
        {
            exporter.addSampleType(SampleTypeService.get().getSampleType(sampleTypeId));
        }

        for (int dataId : _dataIds)
        {
            exporter.addExpData(ExperimentServiceImpl.get().getExpData(dataId));
        }

        for (int dataClassId : _dataClassIds)
        {
            exporter.addDataClass(ExperimentService.get().getDataClass(dataClassId));
        }
    }

    public URLRewriter createURLRewriter()
    {
        return new ArchiveURLRewriter(_includeXarXml, _roles);
    }

    public void addSampleType(ExpSampleType sampleType)
    {
        _sampleTypeIds.add(sampleType.getRowId());
    }

    public void addDataClass(ExpDataClass dataClass)
    {
        _dataClassIds.add(dataClass.getRowId());
    }
}
