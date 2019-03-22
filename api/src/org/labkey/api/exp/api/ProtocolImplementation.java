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

package org.labkey.api.exp.api;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.List;

public class ProtocolImplementation
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
    public void onSamplesChanged(User user, ExpProtocol protocol, List<? extends ExpMaterial> materials) throws SQLException
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
}
