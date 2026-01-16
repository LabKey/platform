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
package org.labkey.api.reports.report;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;

public class ReportType implements AttachmentParentType
{
    private static final ReportType INSTANCE = new ReportType();

    public static ReportType get()
    {
        return INSTANCE;
    }

    private ReportType()
    {
    }

    @Override
    public @NotNull String getUniqueName()
    {
        return "Report";
    }

    @Override
    public @NotNull SQLFragment getSelectEntityIdAndDescriptionSql()
    {
        TableInfo table = CoreSchema.getInstance().getTableInfoReport();
        return new SQLFragment("SELECT EntityId, ")
            .append(table.getSqlDialect().concatenate("ReportKey", "':'", "CAST(RowId AS VARCHAR)"))
            .append(" AS Description FROM ")
            .append(table, "reports");
    }
}
