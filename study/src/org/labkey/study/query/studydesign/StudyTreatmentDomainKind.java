package org.labkey.study.query.studydesign;

import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.study.query.StudyQuerySchema;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User: cnathe
 * Date: 12/17/13
 */
public class StudyTreatmentDomainKind extends AbstractStudyDesignDomainKind
{
    public static final String NAME = "StudyTreatmentDomain";
    public static String NAMESPACE_PREFIX = "StudyDesign-" + NAME;
    private static final Set<PropertyStorageSpec> _baseFields;

    static {
        Set<PropertyStorageSpec> baseFields = new LinkedHashSet<>();
        baseFields.add(createFieldSpec("RowId", JdbcType.INTEGER, true, true));
        baseFields.add(createFieldSpec("Label", JdbcType.VARCHAR).setSize(200).setNullable(false));
        baseFields.add(createFieldSpec("Description", JdbcType.VARCHAR));

        PropertyStorageSpec rendererTypeFieldSpec = createFieldSpec("DescriptionRendererType", JdbcType.VARCHAR).setSize(50).setNullable(false);
        rendererTypeFieldSpec.setDefaultValue("TEXT_WITH_LINKS");
        baseFields.add(rendererTypeFieldSpec);

        _baseFields = Collections.unmodifiableSet(baseFields);
    }

    public StudyTreatmentDomainKind()
    {
        super(StudyQuerySchema.TREATMENT_TABLE_NAME, _baseFields);
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
