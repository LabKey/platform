/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.exp.property;

import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.query.ExpExperimentTable;

import java.util.Set;

/**
 * User: jeckels
 * Date: Jan 27, 2012
 */
public class AssayBatchDomainKind extends AssayDomainKind
{
    public AssayBatchDomainKind()
    {
        super(ExpProtocol.ASSAY_DOMAIN_BATCH);
    }

    public String getKindName()
    {
        return "Assay Batches";
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        Set<String> result = super.getAssayReservedPropertyNames();
        for (ExpExperimentTable.Column column : ExpExperimentTable.Column.values())
        {
            result.add(column.toString());
        }
        result.add("AssayId");
        result.add("Assay Id");
        return result;
    }
}
