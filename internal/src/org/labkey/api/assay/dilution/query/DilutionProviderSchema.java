package org.labkey.api.assay.dilution.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.dilution.DilutionCurve;
import org.labkey.api.assay.dilution.SampleInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.EnumTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayProviderSchema;
import org.labkey.api.study.assay.AssayService;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 5/8/13
 */
public class DilutionProviderSchema extends AssayProviderSchema
{
    public static final String SAMPLE_PREPARATION_METHOD_TABLE_NAME = "SamplePreparationMethod";
    public static final String CURVE_FIT_METHOD_TABLE_NAME = "CurveFitMethod";
    private String _schemaName;

    public DilutionProviderSchema(User user, Container container, AssayProvider provider, String schemaName, @Nullable Container targetStudy, boolean hidden)
    {
        super(user, container, provider, targetStudy);
        _schemaName = schemaName;
        _hidden = hidden;
    }

    public Set<String> getTableNames()
    {
        return getTableNames(false);
    }

    public Set<String> getVisibleTableNames()
    {
        return getTableNames(true);
    }

    protected Set<String> getTableNames(boolean visible)
    {
        Set<String> names = new TreeSet<String>(new Comparator<String>()
        {
            public int compare(String o1, String o2)
            {
                return o1.compareToIgnoreCase(o2);
            }
        });

        names.add(SAMPLE_PREPARATION_METHOD_TABLE_NAME);
        names.add(CURVE_FIT_METHOD_TABLE_NAME);

        return names;
    }

    public TableInfo createTable(String name)
    {
        if (SAMPLE_PREPARATION_METHOD_TABLE_NAME.equalsIgnoreCase(name))
        {
            EnumTableInfo<SampleInfo.Method> result = new EnumTableInfo<SampleInfo.Method>(SampleInfo.Method.class, getDbSchema(),
                    "List of possible sample preparation methods for the " + getProvider().getResourceName() + " assay.", false);
            result.setPublicSchemaName(_schemaName);
            result.setPublicName(SAMPLE_PREPARATION_METHOD_TABLE_NAME);
            return result;
        }
        if (CURVE_FIT_METHOD_TABLE_NAME.equalsIgnoreCase(name))
        {
            EnumTableInfo<DilutionCurve.FitType> result = new EnumTableInfo<DilutionCurve.FitType>(DilutionCurve.FitType.class, getDbSchema(), new EnumTableInfo.EnumValueGetter<DilutionCurve.FitType>()
            {
                public String getValue(DilutionCurve.FitType e)
                {
                    return e.getLabel();
                }
            }, false, "List of possible curve fitting methods for the " + getProvider().getResourceName() + " assay.");
            result.setPublicSchemaName(_schemaName);
            result.setPublicName(CURVE_FIT_METHOD_TABLE_NAME);
            return result;
        }
        return super.createTable(name);
    }
}
