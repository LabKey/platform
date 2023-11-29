package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.HttpUtil;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.HashMap;
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
            if (context.getMethod() == HttpUtil.Method.POST)
            {
                Map<String, Object> pkMap = new HashMap<>();
                getTable().getPkColumns().forEach(pkCol -> {
                    Object pkVal = context.get(pkCol.getName());
                    if (null == pkVal)
                        pkVal = context.get("pk_" + getFormFieldName(pkCol));
                    pkMap.put(pkCol.getName(), pkVal);
                });
                _oldValues = pkMap;
            }
        }
        catch (Exception ignored)
        {
        }
    }
}
