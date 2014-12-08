package org.labkey.di.view;

import org.labkey.api.data.Container;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.VBox;
import org.labkey.di.DataIntegrationQuerySchema;

public class ProcessJobsView extends VBox
{
    public ProcessJobsView(User user, Container container)
    {
        DataIntegrationQuerySchema schema = new DataIntegrationQuerySchema(user, container);
        QuerySettings settings = new QuerySettings(getViewContext(), "processJobs", DataIntegrationQuerySchema.TRANSFORMHISTORY_TABLE_NAME);

        QueryView processedJobsGrid = new QueryView(schema, settings, null);
        HtmlView buttons = new HtmlView(
                PageFlowUtil.button("Scheduler").href(DataIntegrationController.BeginAction.class, container).toString()
        );

        addView(processedJobsGrid);
        addView(buttons);
    }
}
