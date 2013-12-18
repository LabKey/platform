package org.labkey.study.query.studydesign;

import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.study.query.StudyQuerySchema;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by klum on 12/10/13.
 */
public class StudyProductDomainKind extends AbstractStudyDesignDomainKind
{
    public static final String NAME = "StudyProductDomain";
    public static String NAMESPACE_PREFIX = "StudyDesign-" + NAME;
    private static final Set<PropertyStorageSpec> _baseFields;

    static {
        Set<PropertyStorageSpec> baseFields = new LinkedHashSet<>();
        baseFields.add(createFieldSpec("RowId", JdbcType.INTEGER, true, true));
        baseFields.add(createFieldSpec("Label", JdbcType.VARCHAR).setSize(200).setNullable(false));
        baseFields.add(createFieldSpec("Role", JdbcType.VARCHAR).setSize(200));
        baseFields.add(createFieldSpec("Type", JdbcType.VARCHAR).setSize(200));

        _baseFields = Collections.unmodifiableSet(baseFields);
    }

    public StudyProductDomainKind()
    {
        super(StudyQuerySchema.PRODUCT_TABLE_NAME, _baseFields);
    }

/*
    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices()
    {
        return PageFlowUtil.set(new PropertyStorageSpec.Index(false, COLUMN_NAME_ATTACHMENT_PARENT_ENTITY_ID));
    }
*/

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
