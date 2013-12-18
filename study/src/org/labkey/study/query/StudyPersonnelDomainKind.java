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
