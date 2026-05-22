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
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class AssayRunDomainKind extends AssayDomainKind
{
    private static final Set<String> RESERVED_NAMES;
    static
    {
        Set<String> names = new HashSet<>(getAssayReservedPropertyNames());
        names.add("AssayId");
        names.addAll(Arrays.stream(ExpRunTable.Column.values()).map(Enum::name).collect(Collectors.toSet()));

        RESERVED_NAMES = DomainUtil.getNamesAndLabels(names);
    }

    public AssayRunDomainKind()
    {
        super(ExpProtocol.ASSAY_DOMAIN_RUN);
    }

    @Override
    public String getKindName()
    {
        return "Assay Runs";
    }

    @Override
    protected @NotNull Set<String> getKindReservedPropertyNames(Domain domain, User user, boolean forCreate)
    {
        return RESERVED_NAMES;
    }

    @Override
    public Set<String> getMandatoryPropertyNames(Domain domain)
    {
        Set<String> mandatoryNames = super.getMandatoryPropertyNames(domain);

        Pair<AssayProvider, ExpProtocol> pair = findProviderAndProtocol(domain);
        if (pair != null)
        {
            AssayProvider provider = pair.first;
            ExpProtocol protocol = pair.second;
            if (provider != null && protocol != null)
            {
                if (provider.isPlateMetadataEnabled(protocol))
                {
                    mandatoryNames.add(AssayPlateMetadataService.PLATE_SET_COLUMN_NAME);
                    mandatoryNames.add(AssayPlateMetadataService.HIT_SELECTION_CRITERIA_COLUMN_NAME);
                }
            }
        }

        return mandatoryNames;
    }

    @Override
    public boolean allowCalculatedFields()
    {
        return true;
    }
}
