/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.api.exp;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.query.QueryRowReference;

/**
 * Provides some basic recognition for protocols of a particular type.
 */
public interface ExperimentProtocolHandler extends Handler<ExpProtocol>
{
    /**
     * Get a query reference for the protocol type.
     */
    @Nullable QueryRowReference getQueryRowReference(ExpProtocol protocol);

    /**
     * Get a query reference for the run of the protocol type.
     */
    @Nullable QueryRowReference getQueryRowReference(ExpProtocol protocol, ExpRun run);

    /**
     * Get a query reference for the protocol application of the protocol type.
     */
    @Nullable QueryRowReference getQueryRowReference(ExpProtocol protocol, ExpProtocolApplication app);
}
