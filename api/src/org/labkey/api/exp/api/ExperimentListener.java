/*
 * Copyright (c) 2014-2018 LabKey Corporation
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
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.security.User;

import java.util.List;

/**
 * User: vsharma
 * Date: 8/23/2014
 */
public interface ExperimentListener
{
    /** Called before deleting a row from exp.experiment */
    default void beforeExperimentDeleted(Container c, User user, ExpExperiment experiment)
    {}

    default void beforeProtocolsDeleted(Container c, User user, List<? extends ExpProtocol> protocols)
    { }

    // called before the experiment run is created (and saved)
    default void beforeRunCreated(Container container, User user, ExpProtocol protocol, ExpRun run) throws BatchValidationException
    {
    }

    // called after run data is uploaded
    default void afterResultDataCreated(Container container, User user, ExpRun run, ExpProtocol protocol) throws BatchValidationException
    {
    }

    // called before the experiment run is deleted
    default void beforeRunDelete(ExpProtocol protocol, ExpRun run){}

    /** Called before deleting the datas. */
    default void beforeDataDelete(Container c, User user, List<? extends ExpData> data)
    { }

    /** Called after deleting the datas. */
    default void afterDataDelete(Container c, User user, List<? extends ExpData> data) { }

    default void beforeMaterialDelete(List<? extends ExpMaterial> materials, Container container, User user) { }

    default void afterMaterialCreated(List<? extends ExpMaterial> materials, Container container, User user) { }

}
