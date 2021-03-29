package org.labkey.experiment.publish;

import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.publish.PublishKey;
import org.labkey.api.study.query.PublishResultsQueryView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.study.publish.AbstractPublishConfirmAction;
import org.labkey.api.study.publish.PublishConfirmForm;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.labkey.api.util.PageFlowUtil.urlProvider;

@RequiresPermission(InsertPermission.class)
public class SampleTypePublishConfirmAction extends AbstractPublishConfirmAction<SampleTypePublishConfirmAction.SampleTypePublishConfirmForm>
{
    private SamplesSchema _samplesSchema;

    public static class SampleTypePublishConfirmForm extends PublishConfirmForm
    {
        private Integer _rowId;

        public Integer getRowId()
        {
            return _rowId;
        }

        public void setRowId(Integer rowId)
        {
            _rowId = rowId;
        }
    }

    @Override
    public boolean handlePost(SampleTypePublishConfirmForm form, BindException errors) throws Exception
    {
        return super.handlePost(form, errors);
    }

    @Override
    public ModelAndView getView(SampleTypePublishConfirmForm form, boolean reshow, BindException errors) throws Exception
    {
        return super.getView(form, reshow, errors);
    }

    @Override
    protected UserSchema getUserSchema(SampleTypePublishConfirmForm form) //Rosaline temp: Definitely not right
    {

        _samplesSchema = new SamplesSchema(form.getUser(), form.getContainer());
        return _samplesSchema;
    }

    // Rosaline TODO: Update
    @Override
    protected QuerySettings getQuerySettings(SampleTypePublishConfirmForm form)
    {
//        String dataRegionSelectionKey = form.getDataRegionSelectionKey();
//        String[] tempSolutionTODO = dataRegionSelectionKey.split("\\$");
//        String dataRegionName = tempSolutionTODO[2];
//        String queryName = tempSolutionTODO[4];
        String queryName = "name";

        QuerySettings qs = new QuerySettings(form.getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, queryName);
        qs.setSchemaName(SamplesSchema.SCHEMA_NAME);
        qs.setQueryName(queryName);

        getUserSchema(form); //getQuerySettings

        return qs;
    }

    @Override
    protected boolean isMismatchedSpecimenInfo(SampleTypePublishConfirmForm form)
    {
        return false;
    }

    @Override
    protected ActionURL getPublishHandlerURL(SampleTypePublishConfirmForm form)
    {
        return urlProvider(ExperimentUrls.class).getLinkToStudyConfirmURL(getContainer(), null).deleteParameters();
    }

    @Override
    protected FieldKey getObjectIdFieldKey(SampleTypePublishConfirmForm form)
    {
        return null;
    }

    @Override
    protected Map<PublishResultsQueryView.ExtraColFieldKeys, FieldKey> getAdditionalColumns(SampleTypePublishConfirmForm form)
    {
        Map<PublishResultsQueryView.ExtraColFieldKeys, FieldKey> additionalCols = new HashMap<>();
        return additionalCols;
    }

    @Override
    protected Map<String, Object> getHiddenFormFields(SampleTypePublishConfirmForm form)
    {
        Map<String, Object> fields = new HashMap<>();
        return fields;
    }

    @Override
    protected ActionURL copyToStudy(SampleTypePublishConfirmForm form, Container targetStudy, Map<Integer, PublishKey> publishData, List<String> publishErrors)
    {
        return null;
    }

    @Override
    public void addNavTrail(NavTree root)
    {

    }
}
