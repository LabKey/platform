package org.labkey.api.audit.query;

import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;

import java.io.PrintWriter;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 5, 2007
 */
public class DefaultAuditQueryView extends AuditLogQueryView
{
    public DefaultAuditQueryView(UserSchema schema, QuerySettings settings, SimpleFilter filter)
    {
        super(schema, settings, filter);
    }

    public void addDisplayColumn(int index, DisplayColumn dc)
    {
    }

    protected void renderDataRegion(PrintWriter out) throws Exception
    {
    }

    public void renderCustomizeLinks(PrintWriter out) throws Exception
    {
    }

    protected void renderChangeViewPickers(PrintWriter out)
    {
    }
}
