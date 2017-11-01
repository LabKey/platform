/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

package org.labkey.api.flow.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpData;

import java.util.List;

/**
 * User: matthewb
 * Date: Oct 25, 2010
 * Time: 10:18:04 AM
 */
public interface FlowService
{
    /**
     * Flow data files are usually imported via temp files so ExperimentService.get().getExpDataByURL() doesn't work
     * @param canonicalURL
     * @param container
     * @return
     */
    List<ExpData> getExpDataByURL(String canonicalURL, @Nullable Container container);

    int getTempTableCount();
}
