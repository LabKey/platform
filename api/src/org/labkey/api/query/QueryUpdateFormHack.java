package org.labkey.api.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.Map;

// A temporary variant of QueryUpdateForm for actions that rely on deserializing old values. Goal is to identify and
// tag the few actions that need deserialization... and then fix those actions to not rely on this. Actions override
// UserSchemaAction.deserializeOldValues() to specify their reliance on deserialization.
public class QueryUpdateFormHack extends QueryUpdateForm
{
    public QueryUpdateFormHack(@NotNull TableInfo table, @NotNull ViewContext ctx, @Nullable BindException errors)
    {
        super(table, ctx, errors);
    }

    @Override
    public void setViewContext(@NotNull ViewContext context)
    {
        super.setViewContext(context);

        try
        {
            String oldVals = getRequest().getParameter(DataRegion.OLD_VALUES_NAME);
            if (null != StringUtils.trimToNull(oldVals))
            {
                String className = getDynaClass().getName();
                Class beanClass = "className".equals(className) ? Map.class : Class.forName(className);
                _oldValues = PageFlowUtil.decodeObject(beanClass, oldVals);
                _isDataLoaded = true;
            }
        }
        catch (Exception ignored)
        {
        }
    }
}
