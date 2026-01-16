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
package org.labkey.issue.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.issues.IssuesSchema;

public class IssueCommentType implements AttachmentParentType
{
    private static final IssueCommentType INSTANCE = new IssueCommentType();

    public static IssueCommentType get()
    {
        return INSTANCE;
    }

    private IssueCommentType()
    {
    }

    @Override
    public @NotNull String getUniqueName()
    {
        return "IssueComment";
    }

    @Override
    public @NotNull SQLFragment getSelectEntityIdAndDescriptionSql()
    {
        TableInfo table = IssuesSchema.getInstance().getTableInfoComments();

        return new SQLFragment("SELECT EntityId, ")
            .append(table.getSqlDialect().concatenate("'Issue #'", "CAST(IssueId AS VARCHAR)"))
            .append(" AS Description FROM ")
            .append(table, "comments");
    }
}
