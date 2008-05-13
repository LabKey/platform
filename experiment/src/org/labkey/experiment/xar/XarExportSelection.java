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
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.api.ExperimentRun;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.Protocol;

import java.sql.SQLException;
import java.io.Serializable;

/**
 * User: jeckels
 * Date: Oct 31, 2007
 */
public class XarExportSelection implements Serializable
{
    private int _expRowId;
    private int[] _runIds = new int[0];
    private int[] _protocolIds = new int[0];

    public XarExportSelection(int expRowId, int[] runIds)
    {
        _expRowId = expRowId;
        _runIds = runIds;
        assert _runIds.length == 0 || _expRowId != 0;
    }

    public XarExportSelection(int[] protocolIds)
    {
        _protocolIds = protocolIds;
    }

    public void addContent(XarExporter exporter) throws SQLException, ExperimentException
    {
        for (int runId : _runIds)
        {
            ExperimentRun run = ExperimentServiceImpl.get().getExperimentRun(runId);
            exporter.addExperimentRun(run, ExperimentService.get().getExpExperiment(_expRowId));
        }
        for (int protocolId : _protocolIds)
        {
            Protocol protocol = ExperimentServiceImpl.get().getProtocol(protocolId);
            exporter.addProtocol(protocol, true);
        }
    }
}
