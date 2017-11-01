/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.study.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.SQLFragment;
import org.labkey.study.StudySchema;

public class SpecimenRequestEventType implements AttachmentType
{
    private static final SpecimenRequestEventType INSTANCE = new SpecimenRequestEventType();

    public static SpecimenRequestEventType get()
    {
        return INSTANCE;
    }

    private SpecimenRequestEventType()
    {
    }

    @Override
    public @NotNull String getUniqueName()
    {
        return getClass().getName();
    }

    @Override
    public void addWhereSql(SQLFragment sql, String parentColumn, String documentNameColumn)
    {
        sql.append(parentColumn).append(" IN (SELECT EntityId FROM ").append(StudySchema.getInstance().getTableInfoSampleRequestEvent(), "sre").append(")");
    }
}