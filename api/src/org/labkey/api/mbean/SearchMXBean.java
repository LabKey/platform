/*
 * Copyright (c) 2023-2026 LabKey Corporation
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
package org.labkey.api.mbean;

import java.io.IOException;

public interface SearchMXBean
{
    /** @return is the crawler enabled */
    boolean isRunning();

    /** @return is the crawler working on something right now - an indicator that there are a lot of things in the queue */
    boolean isBusy();

    int getNumDocs() throws IOException;
}
