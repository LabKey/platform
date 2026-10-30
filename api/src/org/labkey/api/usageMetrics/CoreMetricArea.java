/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.usageMetrics;

import org.labkey.api.module.DefaultModule;

public enum CoreMetricArea implements SimpleMetricArea
{
    CLIENT_API_REQUESTS("clientApiRequests", "Requests from LabKey client API libraries, by library (Java, Python, R, etc.), identified by user agent"),
    REQUEST_CANCEL("requestCancel", "Cancelled requests by source (timeout, clientDisconnect; shutdown counts database backends instead), plus statementCancelled: running statements cancelled, and backendCancelled: database backends signalled");

    private final Definition _definition;

    CoreMetricArea(String areaName, String description)
    {
        _definition = new Definition(DefaultModule.CORE_MODULE_NAME, areaName, description);
    }

    @Override
    public Definition getDefinition()
    {
        return _definition;
    }
}
