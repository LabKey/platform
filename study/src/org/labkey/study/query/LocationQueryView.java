package org.labkey.study.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.DetailsColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateColumn;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.study.controllers.StudyController;
import org.springframework.validation.Errors;

import java.util.List;

/**
 * Created by Joe on 9/8/2014.
 */
public class LocationQueryView extends QueryView
{
    public LocationQueryView(UserSchema schema, QuerySettings settings, @Nullable Errors errors)
    {
        super(schema, settings, errors);
    }
    @Override
    public DataView createDataView()
    {
        return super.createDataView();
    }
    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        bar.add(super.createViewButton(getViewItemFilter()));
        bar.add(super.createInsertButton());
        bar.add(super.createDeleteButton());
        ActionURL deleteUnusedURL = new ActionURL(StudyController.DeleteAllUnusedLocationsAction.class, getSchema().getContainer());
        deleteUnusedURL.addReturnURL(getReturnURL());
        ActionButton delete = new ActionButton(deleteUnusedURL, "Delete Unused");
        delete.setActionType(ActionButton.Action.LINK);
        delete.setRequiresSelection(false, "Are you sure you want to delete the selected row?", "Are you sure you want to delete the selected rows?");
        deleteUnusedURL.addReturnURL(getViewContext().getActionURL());
        bar.add(delete);

    }

    @Override
    protected void addDetailsAndUpdateColumns(List<DisplayColumn> ret, TableInfo table)
    {
        StringExpression urlDetails = urlExpr(QueryAction.detailsQueryRow);

        if (urlDetails != null && urlDetails != AbstractTableInfo.LINK_DISABLER)
        {
            ret.add(new DetailsColumn(urlDetails, table));
        }

        StringExpression urlUpdate = urlExpr(QueryAction.updateQueryRow);

        if (urlUpdate != null)
        {
            ret.add(0, new UpdateColumn(urlUpdate));
        }
    }


}
