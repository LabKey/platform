package org.labkey.api.query;

import org.labkey.api.data.StringWrapperDynaClass;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.apache.commons.beanutils.DynaBean;

import java.util.Map;

public class QueryWrapperDynaClass extends StringWrapperDynaClass
{
    QueryUpdateForm _form;
    public QueryWrapperDynaClass(QueryUpdateForm form)
    {
        _form = form;

        Map<String, Class> propMap = new CaseInsensitiveHashMap<Class>();
        for (ColumnInfo column : _form.getTable().getColumnsList())
        {
            propMap.put(_form.getFormFieldName(column), column.getJavaClass());
        }

        init("className", propMap);

    }

    

    public DynaBean newInstance() throws IllegalAccessException, InstantiationException
    {
        throw new UnsupportedOperationException();
    }

    public String getPropertyCaption(String propName)
    {
        ColumnInfo column = _form.getColumnByFormFieldName(propName);
        if (column == null)
            return propName;
        return column.getCaption();
    }
}
