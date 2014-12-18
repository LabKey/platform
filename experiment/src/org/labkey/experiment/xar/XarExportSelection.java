/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.experiment.ArchiveURLRewriter;
import org.labkey.experiment.URLRewriter;
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * User: jeckels
 * Date: Oct 31, 2007
 */
public class XarExportSelection implements Serializable
{
    private List<Integer> _expIds = new ArrayList<>();
    private List<Integer> _runIds = new ArrayList<>();
    private List<Integer> _dataIds = new ArrayList<>();
    private List<Integer> _sampleSetIds = new ArrayList<>();
    private List<Integer> _protocolIds = new ArrayList<>();
    private boolean _includeXarXml = true;
    private List<String> _roles;

    public void addExperimentIds(int... expIds)
    {
        for (int expId : expIds)
        {
            _expIds.add(expId);
        }
    }

    public void addRunId(int runId)
    {
        _runIds.add(runId);
    }

    public void addRunIds(Collection<Integer> runIds)
    {
        for (int runId : runIds)
        {
            _runIds.add(runId);
        }
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
            _roles = new ArrayList<>();
        }
        _roles.addAll(Arrays.asList(roles));
    }

    public void addContent(XarExporter exporter) throws SQLException, ExperimentException
    {
        for (int expId : _expIds)
        {
            exporter.addExperiment(ExperimentServiceImpl.get().getExpExperiment(expId));
        }

        for (int runId : _runIds)
        {
            exporter.addExperimentRun(ExperimentServiceImpl.get().getExpRun(runId));
        }

        for (int protocolId : _protocolIds)
        {
            ExpProtocol protocol = ExperimentService.get().getExpProtocol(protocolId);
            exporter.addProtocol(protocol, true);
        }

        for (int sampleSetId : _sampleSetIds)
        {
            exporter.addSampleSet(ExperimentServiceImpl.get().getSampleSet(sampleSetId));
        }

        for (int dataId : _dataIds)
        {
            exporter.addExpData(ExperimentServiceImpl.get().getExpData(dataId));
        }
    }

    public URLRewriter createURLRewriter()
    {
        return new ArchiveURLRewriter(_includeXarXml, _roles);
    }

    public void addSampleSet(ExpSampleSet sampleSet)
    {
        _sampleSetIds.add(sampleSet.getRowId());
    }
}
