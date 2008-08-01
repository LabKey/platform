/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.api.ExperimentRun;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.Protocol;

import java.sql.SQLException;
import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

/**
 * User: jeckels
 * Date: Oct 31, 2007
 */
public class XarExportSelection implements Serializable
{
    private List<Integer> _expIds = new ArrayList<Integer>();
    private List<Integer> _runIds = new ArrayList<Integer>();
    private List<Integer> _protocolIds = new ArrayList<Integer>();

    public void addExperimentIds(int... expIds)
    {
        for (int expId : expIds)
        {
            _expIds.add(expId);
        }
    }

    public void addRunIds(int... runIds)
    {
        for (int runId : runIds)
        {
            _runIds.add(runId);
        }
    }

    public void addProtocolIds(int... protocolIds)
    {
        for (int protocolId : protocolIds)
        {
            _protocolIds.add(protocolId);
        }
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
    }
}
