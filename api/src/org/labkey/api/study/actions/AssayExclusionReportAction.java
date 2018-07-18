package org.labkey.api.study.actions;

import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.MenuButton;
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
import org.labkey.api.study.assay.AssayWellExclusionService;
import org.labkey.api.study.assay.ReplacedRunFilter;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import static org.labkey.api.study.assay.AssayProtocolSchema.EXCLUSION_REPORT_TABLE_NAME;

@RequiresPermission(ReadPermission.class)
@Deprecated // this class will be removed once the code is merged to move this to the premium module
public class AssayExclusionReportAction extends BaseAssayAction<ProtocolIdForm>
{
    public static final FieldKey REPLACED_FIELD_KEY = FieldKey.fromParts("Run", "Replaced");
    private ExpProtocol _protocol;
    private AssayProvider _provider;

    @Override
    public ModelAndView getView(ProtocolIdForm protocolIdForm, BindException errors) throws Exception
    {
        AssayView result = new AssayView();

        _protocol = protocolIdForm.getProtocol();
        _provider = AssayService.get().getProvider(_protocol);
        if (!_provider.isExclusionSupported())
            throw new NotFoundException("Exclusion report not supported for assay type");

        AssayProtocolSchema schema = _provider.createProtocolSchema(getViewContext().getUser(), getViewContext().getContainer(), _protocol, null);
        result.setupViews(getExcludedQueryView(schema, EXCLUSION_REPORT_TABLE_NAME, errors), false, _provider, _protocol);

        return result;
    }

    private QueryView getExcludedQueryView(AssayProtocolSchema schema, String queryName, BindException errors)
    {
        QuerySettings settings = new QuerySettings(getViewContext(), queryName, queryName);
        QueryView view = new QueryView(schema, settings, errors)
        {
            @Override
            public DataView createDataView()
            {
                DataView result = super.createDataView();
                AssayWellExclusionService svc = AssayWellExclusionService.getProvider();
                if (svc != null)
                {
                    ColumnInfo excludedColumn = svc.createExcludedColumn(result.getTable(), _protocol);
                    SimpleFilter excludedFilter = new SimpleFilter(excludedColumn.getFieldKey(), true);
                    result.getRenderContext().setBaseFilter(excludedFilter);
                }

                SimpleFilter filter = (SimpleFilter) result.getRenderContext().getBaseFilter();
                if (filter == null)
                {
                    filter = new SimpleFilter();
                    result.getRenderContext().setBaseFilter(filter);
                }
                ReplacedRunFilter.getFromURL(this, REPLACED_FIELD_KEY).addFilterCondition(filter, REPLACED_FIELD_KEY);
                return result;
            }

            @Override
            protected void populateButtonBar(DataView view, ButtonBar bar)
            {
                super.populateButtonBar(view, bar);
                if (_provider != null && _provider.getReRunSupport() == AssayProvider.ReRunSupport.ReRunAndReplace)
                {
                    MenuButton button = new MenuButton("Replaced Filter");
                    for (ReplacedRunFilter.Type type : ReplacedRunFilter.Type.values())
                    {
                        ActionURL url = view.getViewContext().cloneActionURL();
                        type.addToURL(url, getDataRegionName(), REPLACED_FIELD_KEY);
                        ReplacedRunFilter replacedRunFilter = ReplacedRunFilter.getFromURL(this, REPLACED_FIELD_KEY);
                        button.addMenuItem(type.getTitle(), url).setSelected(type == replacedRunFilter.getType());
                    }
                    bar.add(button);
                }
            }
        };
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
