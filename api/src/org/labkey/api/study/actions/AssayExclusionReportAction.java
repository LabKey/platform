package org.labkey.api.study.actions;

import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import static org.labkey.api.study.assay.AssayProtocolSchema.EXCLUSION_REPORT_TABLE_NAME;
import static org.labkey.api.study.assay.AssayResultTable.FLAGGED_AS_EXCLUDED_COLUMN_NAME;

@RequiresPermission(ReadPermission.class)
public class AssayExclusionReportAction extends BaseAssayAction<ProtocolIdForm>
{
    private ExpProtocol _protocol;

    @Override
    public ModelAndView getView(ProtocolIdForm protocolIdForm, BindException errors) throws Exception
    {
        AssayView result = new AssayView();

        this._protocol = protocolIdForm.getProtocol();
        AssayProvider provider = AssayService.get().getProvider(this._protocol);
        if (!provider.isExclusionSupported())
            throw new NotFoundException("Exclusion report not supported for for assay type");

        AssayProtocolSchema schema = provider.createProtocolSchema(getViewContext().getUser(), getViewContext().getContainer(), this._protocol, null);
        result.setupViews(getExcludedQueryView(schema, EXCLUSION_REPORT_TABLE_NAME, errors), false, provider, this._protocol);

        return result;
    }

    private QueryView getExcludedQueryView(AssayProtocolSchema schema, String queryName, BindException errors)
    {
        QuerySettings settings = new QuerySettings(getViewContext(), queryName, queryName);
        SimpleFilter exclusionFilter = new SimpleFilter(FieldKey.fromParts(FLAGGED_AS_EXCLUDED_COLUMN_NAME), true);
        settings.setBaseFilter(exclusionFilter);
        QueryView view = new QueryView(schema, settings, errors);
        view.setTitle("Excluded Rows");
        String helpText = "Shows all of the data rows that have been marked as excluded in this folder. Data may be marked as excluded from the results views.";
        view.setTitlePopupHelp("Excluded data rows", helpText);
        return view;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        NavTree result = super.appendNavTrail(root);
        result.addChild(_protocol.getName() + " Exclusions");
        return result;
    }
}
