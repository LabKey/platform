package org.labkey.api.query;

import org.labkey.api.data.DataColumn;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.apache.commons.lang.ObjectUtils;

public class UserIdRenderer extends DataColumn
{
    static public boolean isGuestUserId(Object value)
    {
        return ObjectUtils.equals(value, 0);
    }
    static public class GuestAsBlank extends DataColumn
    {
        public GuestAsBlank(ColumnInfo column)
        {
            super(column);
        }

        public String getFormattedValue(RenderContext ctx)
        {
            if (isGuestUserId(getBoundColumn().getValue(ctx)))
                return "&nbsp;";
            return super.getFormattedValue(ctx);
        }
    }

    public UserIdRenderer(ColumnInfo column)
    {
        super(column);
    }

    public String getFormattedValue(RenderContext ctx)
    {
        if (isGuestUserId(getBoundColumn().getValue(ctx)))
        {
            return "Guest";
        }
        return super.getFormattedValue(ctx);
    }
}
