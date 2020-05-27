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

package org.labkey.api.exp.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentProtocolHandler;
import org.labkey.api.query.QueryRowReference;
import org.labkey.api.security.User;

import java.util.List;

public class ProtocolImplementation implements ExperimentProtocolHandler
{
    final protected String _name;
    public ProtocolImplementation(String name)
    {
        _name = name;
    }

    public String getName()
    {
        return _name;
    }

    /**
     * Called when samples in a sample set have one or more properties modified.  Also called when new samples are
     * created (uploaded).  This is not called when samples are deleted.
     * @param protocol whose {@link org.labkey.api.exp.property.ExperimentProperty#SampleSetLSID} property
     * is the sampleset that these samples came from.
     * @param materials materials that were modified.
     */
    public void onSamplesChanged(User user, ExpProtocol protocol, List<? extends ExpMaterial> materials)
    {
    }

    public boolean deleteRunWhenInputDeleted()
    {
        return false;
    }

    /**
     * Called after a ExpRun and all its ExpDatas have been deleted.
     * @param container The container the run was deleted from.
     * @param user The user who deleted the run.
     */
    public void onRunDeleted(Container container, User user)
    {
    }

    @Override
    public @Nullable Priority getPriority(ExpProtocol protocol)
    {
        if (getName().equals(protocol.getImplementationName()))
            return Priority.HIGH;

        return null;
    }

    /**
     * Get a query reference for the protocol type.
     */
    @Override
    public QueryRowReference getQueryRowReference(ExpProtocol protocol)
    {
        return null;
    }

    /**
     * Get a query reference for the run of the protocol type.
     */
    @Override
    public QueryRowReference getQueryRowReference(ExpProtocol protocol, ExpRun run)
    {
        return null;
    }

    /**
     * Get a query reference for the protocol application of the protocol type.
     */
    @Override
    public QueryRowReference getQueryRowReference(ExpProtocol protocol, ExpProtocolApplication app)
    {
        return null;
    }
}
