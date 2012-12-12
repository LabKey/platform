package org.labkey.survey.query;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.survey.SurveyController;
import org.springframework.validation.BindException;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 12/10/12
 */
public class SurveyDesignQueryView extends QueryView
{
    public SurveyDesignQueryView(UserSchema schema, QuerySettings settings, BindException errors)
    {
        super(schema, settings, errors);

        settings.setAllowChooseQuery(false);

        setShowDeleteButton(true);
        setShowInsertNewButton(false);
        setShowUpdateColumn(true);
        setShowExportButtons(false);
        setShowImportDataButton(false);
        setShowReports(false);
        setShowRecordSelectors(true);
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        ActionURL insertURL = new ActionURL(SurveyController.UpdateSurveyDesignAction.class, getContainer());
        insertURL.addParameter(QueryParam.srcURL, getReturnURL().toString());

        ActionButton insert = new ActionButton(insertURL, "Add New Survey");
        insert.setActionType(ActionButton.Action.LINK);

        bar.add(insert);
    }
}
