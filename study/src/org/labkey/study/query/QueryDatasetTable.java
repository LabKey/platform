/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.DatasetDomainKind;

import java.util.Collections;
import java.util.List;

public class QueryDatasetTable extends DatasetTableImpl
{
    public static final String[] REQUIRED_COLUMNS = new String[]{
        DatasetDomainKind._KEY,
        DatasetDomainKind.DATE,
        DatasetDomainKind.QCSTATE,
        DatasetDomainKind.LSID,
        DatasetDomainKind.PARTICIPANTID
    };

    QueryDatasetTable(@NotNull StudyQuerySchema schema, ContainerFilter cf, @NotNull DatasetDefinition dsd)
    {
        super(schema, cf, dsd);

        setUpdateURL(null);
        setInsertURL(null);
        setImportURL(null);
        setDeleteURL(null);

        MutableColumnInfo ci = getMutableColumn("_key");
        if (ci != null)
        {
            ci.setHidden(true);
        }
    }

    @Override
    public @NotNull List<IndexDefinition> getUniqueIndices()
    {
        return Collections.emptyList();
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return null;
    }
}
