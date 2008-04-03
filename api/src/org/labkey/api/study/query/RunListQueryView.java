package org.labkey.api.study.query;

import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.exp.ExperimentRunListView;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.assay.AssayRunFilter;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ActionButton;

/**
 * User: brittp
 * Date: Jun 29, 2007
 * Time: 11:13:44 AM
 */
public class RunListQueryView extends ExperimentRunListView
{
    private ExpProtocol _protocol;
    public RunListQueryView(ExpProtocol protocol, UserSchema schema, QuerySettings settings, AssayRunFilter assayRunFilter)
    {
        super(schema, settings, assayRunFilter);
        _protocol = protocol;
        setShowDeleteButton(true);
        setShowAddToRunGroupButton(true);
    }

    public RunListQueryView(ExpProtocol protocol, ViewContext context)
    {
        this(protocol, getDefaultUserSchema(context),
                getDefaultQuerySettings(protocol, context), getDefaultAssayRunFilter(protocol, context));
    }

    public static AssayRunFilter getDefaultAssayRunFilter(ExpProtocol protocol, ViewContext context)
    {
        return new AssayRunFilter(protocol, context.getContainer());
    }

    public static QuerySettings getDefaultQuerySettings(ExpProtocol protocol, ViewContext context)
    {
        UserSchema schema = getDefaultUserSchema(context);
        return ExperimentRunListView.getRunListQuerySettings(schema, context, AssayRunFilter.getRunListTableName(protocol), true);
    }

    public static UserSchema getDefaultUserSchema(ViewContext context)
    {
        return QueryService.get().getUserSchema(context.getUser(), context.getContainer(), AssayRunFilter.SCHEMA_NAME);
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        ActionURL target = AssayService.get().getProtocolURL(getContainer(), _protocol, "showSelectedData");
        ActionButton viewSelectedButton = new ActionButton(target, "Show Data For Runs");
        viewSelectedButton.setScript("return verifySelected(this.form, \"" + target.getLocalURIString() + "\", \"post\", \"runs\")");
        viewSelectedButton.setActionType(ActionButton.Action.POST);
        bar.add(viewSelectedButton);
    }
}
