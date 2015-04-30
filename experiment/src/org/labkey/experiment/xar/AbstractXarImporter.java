/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.fhcrc.cpas.exp.xml.ExperimentArchiveType;
import org.labkey.api.data.Container;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.security.User;

/**
 * User: jeckels
 * Date: Jul 5, 2007
 */
public abstract class AbstractXarImporter
{
    protected final XarSource _xarSource;
    protected final PipelineJob _job;
    protected ExperimentArchiveType _experimentArchive;

    public AbstractXarImporter(XarSource source, PipelineJob job)
    {
        _xarSource = source;
        _job = job;
    }

    protected void checkDataCpasType(String declaredType)
    {
        if (declaredType != null && !ExpData.DEFAULT_CPAS_TYPE.equals(declaredType))
        {
            _job.getLogger().warn("Unrecognized CpasType '" + declaredType + "' loaded for Data object.");
        }
    }

    protected void checkMaterialCpasType(String declaredType) throws XarFormatException
    {
        if (declaredType != null && !ExpMaterial.DEFAULT_CPAS_TYPE.equals(declaredType))
        {
            if (ExperimentService.get().getSampleSet(declaredType) != null)
            {
                return;
            }

            _job.getLogger().warn("Unrecognized CpasType '" + declaredType + "' loaded for Material object.");
        }
    }

    protected void checkProtocolApplicationCpasType(String cpasType, Logger logger)
    {
        if (cpasType != null &&
                !ExpProtocol.ApplicationType.ProtocolApplication.toString().equals(cpasType) &&
                !ExpProtocol.ApplicationType.ExperimentRun.toString().equals(cpasType) &&
                !ExpProtocol.ApplicationType.ExperimentRunOutput.toString().equals(cpasType))
        {
            logger.warn("Unrecognized CpasType '" + cpasType + "' loaded for ProtocolApplication object.");
        }
    }

    protected User getUser() { return _job.getUser(); }
    protected Logger getLog() { return _job.getLogger(); }
    protected Container getContainer() { return _job.getContainer(); }
    protected XarContext getRootContext() { return _xarSource.getXarContext(); }
}
