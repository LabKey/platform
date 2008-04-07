package org.labkey.experiment.controllers.exp;

import org.labkey.api.view.*;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.exp.api.ExpSchema;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.data.*;
import org.labkey.api.security.User;

/**
 * User: jeckels
 * Date: Jan 28, 2008
 */
public class ExperimentRunGroupsView extends VBox
{
    public ExperimentRunGroupsView(User user, final Container c, final ExpRun run, final ActionURL currentURL)
    {
        QuerySettings settings = new QuerySettings(getViewContext().getActionURL(), "Experiments", ExpSchema.EXPERIMENTS_MEMBERSHIP_FOR_RUN_TABLE_NAME);
        settings.setAllowChooseQuery(false);
        QueryView experimentsView = new QueryView(new ExpSchema(user, c), settings)
        {
            protected TableInfo createTable()
            {
                return ((ExpSchema)getSchema()).createExperimentsTableWithRunMemberships(null, run);
            }

            protected void populateButtonBar(DataView view, ButtonBar bar)
            {
                ActionButton createButton = new ActionButton(ExperimentController.ExperimentUrlsImpl.get().getCreateRunGroupURL(c, currentURL, false), "Create new group");
                bar.add(createButton);

                bar.add(new SimpleTextDisplayElement("<span id=\"experimentRunGroupMembershipStatus\" />", true));
            }
        };
        experimentsView.setShowCustomizeViewLinkInButtonBar(true);
        experimentsView.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTTOM);
        experimentsView.setShowDetailsColumn(false);
        HtmlView explanationView = new HtmlView("Experiment run groups let you define sets of runs that are related. For example, you might create separate groups\n" +
                "for your case and controls, so that you can easily select them.");

        addView(explanationView);
        addView(experimentsView);


        Collapsible collapsible = new WebPartCollapsible("experimentRunGroup");
        NavTreeManager.applyExpandState(collapsible, getViewContext());
        enableExpandCollapse("experimentRunGroup", collapsible == null || !collapsible.isCollapsed());
        setTitle("Experiment Run Groups");
        setFrame(FrameType.PORTAL);
    }
}
