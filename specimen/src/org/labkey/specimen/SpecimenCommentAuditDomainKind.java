/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.specimen;

import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class SpecimenCommentAuditDomainKind extends AbstractAuditDomainKind
{
    public static final String NAME = "SpecimenCommentAuditDomain";
    public static final String SPECIMEN_COMMENT_EVENT = "SpecimenCommentEvent";
    public static final String NAMESPACE_PREFIX = "Audit-" + NAME;
    public static final String COLUMN_NAME_VIAL_ID = "VialId";

    private final Set<PropertyDescriptor> _fields;

    public SpecimenCommentAuditDomainKind()
    {
        super(SPECIMEN_COMMENT_EVENT);

        Set<PropertyDescriptor> fields = new LinkedHashSet<>();
        fields.add(createPropertyDescriptor(COLUMN_NAME_VIAL_ID, PropertyType.STRING));
        _fields = Collections.unmodifiableSet(fields);
    }

    @Override
    public Set<PropertyDescriptor> getProperties()
    {
        return _fields;
    }

    @Override
    protected String getNamespacePrefix()
    {
        return NAMESPACE_PREFIX;
    }

    @Override
    public String getKindName()
    {
        return NAME;
    }
}
