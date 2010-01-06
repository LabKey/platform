package org.labkey.core.workbook;

import org.labkey.api.query.QueryView;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.core.query.CoreQuerySchema;

/**
 * Created by IntelliJ IDEA.
 * User: labkey
 * Date: Jan 5, 2010
 * Time: 5:09:55 PM
 */
public class WorkbookQueryView extends QueryView
{
    public WorkbookQueryView(ViewContext ctx, CoreQuerySchema schema)
    {
        super(schema);

        QuerySettings settings = new QuerySettings(ctx, QueryView.DATAREGIONNAME_DEFAULT);
        settings.setSchemaName(schema.getSchemaName());
        settings.setQueryName(CoreQuerySchema.WORKBOOKS_TABLE_NAME);
        settings.setAllowChooseQuery(false);
        settings.setContainerFilterName(ContainerFilter.Type.CurrentAndSubfolders.name());
        setSettings(settings);

        setTitle("Workbooks");
    }
}
