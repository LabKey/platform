/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.study.query.studydesign.AbstractStudyDesignDomainKind;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by klum on 12/17/13.
 */
public class StudyPersonnelDomainKind extends AbstractStudyDesignDomainKind
{
    public static final String NAME = "StudyPersonnelDomain";
    public static String NAMESPACE_PREFIX = "Study-" + NAME;
    private static final Set<PropertyStorageSpec> _baseFields;

    static {
        Set<PropertyStorageSpec> baseFields = new LinkedHashSet<>();
        baseFields.add(createFieldSpec("RowId", JdbcType.INTEGER, true, true));
        baseFields.add(createFieldSpec("Label", JdbcType.VARCHAR).setSize(200).setNullable(false));
        baseFields.add(createFieldSpec("Role", JdbcType.VARCHAR).setSize(200));
        baseFields.add(createFieldSpec("URL", JdbcType.VARCHAR).setSize(200));
        baseFields.add(createFieldSpec("UserId", JdbcType.INTEGER));

        _baseFields = Collections.unmodifiableSet(baseFields);
    }

    public StudyPersonnelDomainKind()
    {
        super(StudyQuerySchema.PERSONNEL_TABLE_NAME, _baseFields);
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
