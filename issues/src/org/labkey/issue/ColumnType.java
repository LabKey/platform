/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.issue;

import org.jetbrains.annotations.NotNull;
import org.labkey.issue.model.Issue;

import java.util.Map;

/**
 * Created by klum on 4/18/2016.
 */
@Deprecated // This class can be deleted in 19.1
public interface ColumnType
{
    int getOrdinal();
    String getColumnName();
    boolean isStandard();
    boolean isCustomString();
    boolean isCustomInteger();
    boolean isCustom();

    // Most pick lists display a blank entry
    boolean allowBlank();
    @NotNull String[] getInitialValues();
    @NotNull String getInitialDefaultValue();
}
