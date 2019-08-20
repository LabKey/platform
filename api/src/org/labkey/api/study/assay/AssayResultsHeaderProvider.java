/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ActionButton;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.view.ViewContext;

import java.util.Collections;
import java.util.List;

public interface AssayResultsHeaderProvider
{
    @NotNull
    default List<ActionButton> getButtons(AssayProvider provider, ExpProtocol protocol, ViewContext viewContext, String runId)
    {
        return Collections.emptyList();
    }
}
