package org.labkey.mothership.view;

import org.labkey.api.data.Container;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.User;
import org.labkey.api.view.VBox;
import org.labkey.mothership.query.MothershipSchema;
import org.springframework.validation.BindException;

public class ExceptionListWebPart extends VBox
{
    public ExceptionListWebPart(User user, Container container, BindException errors)
    {
        MothershipSchema schema = new MothershipSchema(user, container);
        QuerySettings settings = schema.getSettings(getViewContext(), "ExceptionSummary", MothershipSchema.EXCEPTION_STACK_TRACE_TABLE_NAME);
        settings.getBaseSort().insertSortColumn(FieldKey.fromParts("ExceptionStackTraceId"), Sort.SortDirection.DESC);

        QueryView queryView = schema.createView(getViewContext(), settings, errors);
        queryView.setShowDetailsColumn(false);
        queryView.setShadeAlternatingRows(true);
        queryView.setShowBorders(true);

        addView(new LinkBar());
        addView(queryView);
    }
}
