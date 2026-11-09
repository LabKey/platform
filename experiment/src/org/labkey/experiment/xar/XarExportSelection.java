/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

/**
 * User: jeckels
 * Date: Oct 31, 2007
 */
public class XarExportSelection implements Serializable
{
    // Store ids instead of experiment objects so that it's easily serializable
    private final List<Long> _expIds = new ArrayList<>();
    private final Set<Long> _runIds = new LinkedHashSet<>();
    private final List<Long> _dataIds = new ArrayList<>();
    private final List<Long> _sampleTypeIds = new ArrayList<>();
    private final List<Long> _protocolIds = new ArrayList<>();
    private final List<Long> _dataClassIds = new ArrayList<>();

    private boolean _includeXarXml = true;
    private Set<String> _roles;

    public void addExperimentIds(long... expIds)
    {
        for (long expId : expIds)
        {
            _expIds.add(expId);
        }
    }

    public void addRuns(Collection<? extends ExpRun> runs)
    {
        // Issue 44306 - be sure to retain the ordering of the runs as passed in the collection
        _runIds.addAll(runs.stream().map(ExpObject::getRowId).toList());
    }

    public void addRunIds(Collection<Long> runIds)
    {
        _runIds.addAll(runIds);
    }

    public void addDataIds(long... dataIds)
    {
        for (long dataId : dataIds)
        {
            _dataIds.add(dataId);
        }
    }

    public void addProtocolIds(long... protocolIds)
    {
        for (long protocolId : protocolIds)
        {
            _protocolIds.add(protocolId);
        }
    }

    public void addProtocolIds(Collection<Long> protocolIds)
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
        for (long expId : _expIds)
        {
            exporter.addExperiment(ExperimentServiceImpl.get().getExpExperiment(expId));
        }

        for (long protocolId : _protocolIds)
        {
            ExpProtocol protocol = ExperimentService.get().getExpProtocol(protocolId);
            exporter.addProtocol(protocol, true);
        }

        // Process runs after protocols because we want to assure the protocols are all defined
        // since SM Workflow Tasks can reference assay design protocols
        for (long runId : _runIds)
        {
            exporter.addExperimentRun(ExperimentService.get().getExpRun(runId));
        }

        for (long sampleTypeId : _sampleTypeIds)
        {
            exporter.addSampleType(SampleTypeService.get().getSampleType(sampleTypeId));
        }

        for (var dataId : _dataIds)
        {
            exporter.addExpData(ExperimentServiceImpl.get().getExpData(dataId));
        }

        for (var dataClassId : _dataClassIds)
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
