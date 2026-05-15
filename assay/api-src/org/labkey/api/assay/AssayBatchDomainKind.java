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
package org.labkey.api.assay;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.query.ExpExperimentTable;
import org.labkey.api.security.User;

import java.util.Arrays;
import java.util.Set;

/**
 * User: jeckels
 * Date: Jan 27, 2012
 */
public class AssayBatchDomainKind extends AssayDomainKind
{
    private static final Set<String> RESERVED_NAMES;

    static {
        RESERVED_NAMES = DomainUtil.getNamesAndLabels(Arrays.stream(ExpExperimentTable.Column.values()).map(ExpExperimentTable.Column::name).toList());
        RESERVED_NAMES.addAll(getAssayReservedPropertyNames());
        RESERVED_NAMES.addAll(DomainUtil.getNameAndLabels("AssayId"));
    }
    public AssayBatchDomainKind()
    {
        super(ExpProtocol.ASSAY_DOMAIN_BATCH);
    }

    @Override
    public String getKindName()
    {
        return "Assay Batches";
    }

    @Override
    public @NotNull Set<String> getReservedPropertyNames(Domain domain, User user)
    {
        return RESERVED_NAMES;
    }

    @Override
    public boolean allowCalculatedFields()
    {
        return true;
    }
}
