package org.labkey.study.query.studydesign;

import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.study.query.StudyQuerySchema;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by klum on 12/13/13.
 */
public class StudyTreatmentProductDomainKind extends AbstractStudyDesignDomainKind
{
    public static final String NAME = "StudyTreatmentProductDomain";
    public static String NAMESPACE_PREFIX = "StudyDesign-" + NAME;
    private static final Set<PropertyStorageSpec> _baseFields;

    static {
        Set<PropertyStorageSpec> baseFields = new LinkedHashSet<>();
        baseFields.add(createFieldSpec("RowId", JdbcType.INTEGER, true, true));
        baseFields.add(createFieldSpec("TreatmentId", JdbcType.INTEGER).setNullable(false));
        baseFields.add(createFieldSpec("ProductId", JdbcType.INTEGER).setNullable(false));
        baseFields.add(createFieldSpec("Dose", JdbcType.VARCHAR).setSize(200));
        baseFields.add(createFieldSpec("Route", JdbcType.VARCHAR).setSize(200));

        _baseFields = Collections.unmodifiableSet(baseFields);
    }

    public StudyTreatmentProductDomainKind()
    {
        super(StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME, _baseFields);
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
