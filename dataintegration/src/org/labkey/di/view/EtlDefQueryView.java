package org.labkey.di.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.Sort;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ActionURL;
import org.springframework.validation.BindException;

import java.util.Collections;

/**
 * User: tgaluhn
 * Date: 6/7/2018
 */
public class EtlDefQueryView extends QueryView
{
    public EtlDefQueryView(UserSchema schema, @NotNull QuerySettings settings, BindException errors)
    {
        super(schema, settings, errors);
        settings.setBaseSort(new Sort("Name"));
        init();
    }

    private void init()
    {
        setTitle("Custom ETL Definitions");
        setShowExportButtons(false);
        setShowReports(false);
        setShowImportDataButton(false);
        ActionURL detailsUrl = new ActionURL(DataIntegrationController.DefinitionDetailsAction.class, getContainer());
        setDetailsURL(new DetailsURL(detailsUrl, Collections.singletonMap("etlDefId", FieldKey.fromString("etlDefId"))).toString());
    }

    @Override
    public @Nullable ActionButton createDeleteButton()
    {
        ActionButton delete = super.createDeleteButton();
        if (null != delete)
        {
            delete.setRequiresSelection(true);
        }
        return delete;
    }
}
