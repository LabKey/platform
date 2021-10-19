package org.labkey.study.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayQCService;
import org.labkey.api.assay.AssayTableMetadata;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.assay.actions.ProtocolIdForm;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.study.publish.AbstractPublishStartAction;
import org.labkey.api.study.publish.PublishStartForm;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpPostRedirectView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.labkey.api.util.PageFlowUtil.urlProvider;

@RequiresPermission(InsertPermission.class)
public class AssayPublishStartAction extends AbstractPublishStartAction<AssayPublishStartAction.AssayPublishStartForm>
{
    private ExpProtocol _protocol;
    private AssayProvider _provider;
    private List<Integer> _ids = new ArrayList<>();
    private List<Integer> _runIds = new ArrayList<>();

    public static class AssayPublishStartForm extends ProtocolIdForm implements PublishStartForm
    {
        private String _dataRegionSelectionKey;
        private String _containerFilterName;
        private boolean _runIds;
        private boolean _isAutoLinkEnabled;

        @Override
        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        @Override
        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            _dataRegionSelectionKey = dataRegionSelectionKey;
        }

        @Override
        public boolean isAutoLinkEnabled()
        {
            return _isAutoLinkEnabled;
        }

        public void setAutoLinkEnabled(boolean autoLinkEnabled)
        {
            _isAutoLinkEnabled = autoLinkEnabled;
        }


        public String getContainerFilterName()
        {
            return _containerFilterName;
        }

        public void setContainerFilterName(String containerFilterName)
        {
            _containerFilterName = containerFilterName;
        }

        public boolean isRunIds()
        {
            return _runIds;
        }

        public void setRunIds(boolean runIds)
        {
            _runIds = runIds;
        }

        @Override
        public @Nullable ActionURL getReturnActionURL()
        {
            return super.getReturnActionURL() != null
                    ? super.getReturnActionURL()
                    : urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), getProtocol()).addParameter("clearDataRegionSelectionKey", getDataRegionSelectionKey());
        }
    }

    @Override
    public ModelAndView getView(AssayPublishStartForm form, BindException errors)
    {
        // initialize the result row ids
        List<Integer> ids = getDataIDs(form);

        // if QC is enabled for this protocol, verify that the selected data has been approved, otherwise show an error
        if (!validateQCState(_runIds, ids))
        {
            return new HtmlView(HtmlString.unsafe("<span class='labkey-error'>QC checks failed. There are unapproved rows of data in the link to study selection, " +
                    "please change your selection or request a QC Analyst to approve the run data.</span>"));
        }

        // If the TargetStudy column is on the result domain, redirect past the choose target study page directly to the confirm page.
        Pair<ExpProtocol.AssayDomainTypes, DomainProperty> pair = _provider.findTargetStudyProperty(_protocol);
        if (pair != null && pair.first == ExpProtocol.AssayDomainTypes.Result)
        {
            Collection<Pair<String, String>> inputs = new ArrayList<>();
            inputs.add(Pair.of(QueryParam.containerFilterName.name(), form.getContainerFilterName()));
            if (form.getReturnUrl() != null)
                inputs.add(Pair.of(ActionURL.Param.returnUrl.name(), form.getReturnUrl().toString()));
            inputs.add(Pair.of(DataRegionSelection.DATA_REGION_SELECTION_KEY, form.getDataRegionSelectionKey()));
            for (Integer id : ids)
                inputs.add(Pair.of(DataRegion.SELECT_CHECKBOX_NAME, id.toString()));

            // Copy url parameters to hidden inputs
            ActionURL url = urlProvider(StudyUrls.class).getLinkToStudyConfirmURL(getContainer(), _protocol);
            for (Pair<String, String> parameter : url.getParameters())
                inputs.add(parameter);

            url.deleteParameters();
            getPageConfig().setTemplate(PageConfig.Template.None);
            return new HttpPostRedirectView(url.toString(), inputs);
        }

        return super.getView(form, errors);
    }

    @Override
    protected ActionURL getSuccessUrl(AssayPublishStartForm form)
    {
        return urlProvider(StudyUrls.class).getLinkToStudyConfirmURL(getContainer(), _protocol );
    }

    @Override
    protected List<Integer> getDataIDs(AssayPublishStartForm form)
    {
        if (_ids.isEmpty())
        {
            _protocol = form.getProtocol();
            _provider = form.getProvider();

            form.setAutoLinkEnabled(form.getProtocol().getObjectProperties().get(StudyPublishService.AUTO_LINK_TARGET_PROPERTY_URI) != null);

            AssayTableMetadata tableMetadata = _provider.getTableMetadata(_protocol);
            if (form.isRunIds())
            {
                // Need to convert the run ids into data row ids
                _runIds = getCheckboxIds(getViewContext());
                DataRegionSelection.clearAll(getViewContext(), null);
                // Get the assay results table
                UserSchema schema = _provider.createProtocolSchema(getUser(), getContainer(), _protocol, null);
                TableInfo table = schema.getTableCFF(AssayProtocolSchema.DATA_TABLE_NAME, ContainerFilter.getType(form.getContainerFilterName()));
                final ColumnInfo dataRowIdColumn = QueryService.get().getColumns(table, Collections.singleton(tableMetadata.getResultRowIdFieldKey())).get(tableMetadata.getResultRowIdFieldKey());
                assert dataRowIdColumn  != null : "Could not find dataRowId column in assay results table";
                FieldKey runFieldKey = tableMetadata.getRunRowIdFieldKeyFromResults();
                ColumnInfo runIdColumn = QueryService.get().getColumns(table, Collections.singleton(runFieldKey)).get(runFieldKey);
                assert runIdColumn  != null : "Could not find runId column in assay results table";

                // Filter it to get only the rows from this set of runs
                SimpleFilter filter = new SimpleFilter();
                filter.addClause(new SimpleFilter.InClause(runFieldKey, _runIds, true));

                // Pull out the data row ids
                _ids = new ArrayList<>();
                new TableSelector(table, Arrays.asList(dataRowIdColumn, runIdColumn), filter, new Sort(runFieldKey.toString())).setForDisplay(true).forEach(rs -> _ids.add(dataRowIdColumn.getIntValue(rs)));
            }
            else
            {
                _ids = getCheckboxIds(getViewContext());
            }
        }
        return _ids;
    }

    @Override
    protected Set<Container> getAssociatedStudyContainers(AssayPublishStartForm form)
    {
        return _provider.getAssociatedStudyContainers(_protocol, _ids);
    }

    @Override
    protected List<Integer> getBatchIds()
    {
        return _runIds;
    }

    @Override
    protected String getBatchNoun()
    {
        return "run";
    }

    /**
     * Determines whether any of the runs or data rows passed in have an unapproved QC state.
     * The protocol must support QC and have been configured for QC, otherwise it will always return
     * true.
     *
     * @return true if all runs or data are approved, else false
     */
    private boolean validateQCState(List<Integer> runIds, List<Integer> dataIds)
    {
        if (AssayQCService.getProvider().supportsQC())
        {
            try
            {
                if (!runIds.isEmpty())
                {
                    return AssayQCService.getProvider().getUnapprovedRuns(_protocol, runIds).isEmpty();
                }
                else
                {
                    return AssayQCService.getProvider().getUnapprovedData(_protocol, dataIds).isEmpty();
                }

            }
            catch (ExperimentException e)
            {
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        setHelpTopic("publishAssayData");

        root.addChild("Assay List", urlProvider(AssayUrls.class).getAssayListURL(getContainer()));
        root.addChild(_protocol.getName(), urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol));
        root.addChild("Link to Study: Choose Target");
    }
}
