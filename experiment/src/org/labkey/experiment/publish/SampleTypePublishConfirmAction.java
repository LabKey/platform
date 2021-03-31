package org.labkey.experiment.publish;

import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.publish.PublishKey;
import org.labkey.api.study.query.PublishResultsQueryView;
import org.labkey.api.util.HtmlString;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NavTree;
import org.labkey.api.study.publish.AbstractPublishConfirmAction;
import org.labkey.api.study.publish.PublishConfirmForm;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.labkey.api.util.PageFlowUtil.urlProvider;

@RequiresPermission(InsertPermission.class)
public class SampleTypePublishConfirmAction extends AbstractPublishConfirmAction<SampleTypePublishConfirmAction.SampleTypePublishConfirmForm>
{
    private SamplesSchema _sampleTypeSchema;
    private ExpSampleType _sampleType;
    private final String ROW_ID = ExpMaterialTable.Column.RowId.toString();

    public static class SampleTypePublishConfirmForm extends PublishConfirmForm
    {
        private Integer _rowId;
        private ExpSampleType _sampleType;

        public Integer getRowId()
        {
            return _rowId;
        }

        public void setRowId(Integer rowId)
        {
            _rowId = rowId;
        }

        // Rosaline temp note: Parts v similar to assay case here
        public ExpSampleType getSampleType()
        {
            if (_sampleType == null)
            {
                if (getRowId() != null)
                    _sampleType = SampleTypeService.get().getSampleType(getContainer(), getRowId());
                else
                    throw new NotFoundException("Sample Type ID not specified.");

                if (_sampleType == null)
                    throw new NotFoundException("Sample Type " + getRowId() + " does not exist in " + getContainer().getPath());

                if (!_sampleType.getContainer().hasPermission(getViewContext().getUser(), ReadPermission.class))
                    throw new UnauthorizedException();
            }
            return _sampleType;
        }

        public void setSampleType(ExpSampleType sampleType)
        {
            _sampleType = sampleType;
        }
    }

    public ExpSampleType getSampleType()
    {
        return _sampleType;
    }

    public void setSampleType(ExpSampleType sampleType)
    {
        _sampleType = sampleType;
    }

    @Override
    public boolean handlePost(SampleTypePublishConfirmForm form, BindException errors) throws Exception
    {
        return super.handlePost(form, errors);
    }

    /**
     * Names need to match those found in PublishConfirmForm.DefaultValueSource
     */
    public enum DefaultValueSource
    {
        PublishSource
                {
                    @Override
                    public FieldKey getParticipantIDFieldKey()
                    {
                        return FieldKey.fromParts("ParticipantID"); // Rosaline TODO
                    }
                    @Override
                    public FieldKey getVisitIDFieldKey(TimepointType type)
                    {
                        if (type == TimepointType.DATE)
                        {
                            return FieldKey.fromParts("Date");
                        }
                        else if (type == TimepointType.VISIT)
                        {
                            return FieldKey.fromParts("VisitID");
                        }
                        else
                        {
                            return null;
                        }
                    }
                },
        UserSpecified
            {
                @Override
                public FieldKey getParticipantIDFieldKey()
                {
                    return null;
                }
                @Override
                public FieldKey getVisitIDFieldKey(TimepointType type)
                {
                    return null;
                }
            };

        public abstract FieldKey getParticipantIDFieldKey();
        public abstract FieldKey getVisitIDFieldKey(TimepointType type);
    }

    @Override
    public void validateCommand(SampleTypePublishConfirmForm form, Errors errors)
    {
        super.validateCommand(form, errors);
        _sampleType = form.getSampleType();
    }

    @Override
    public ModelAndView getView(SampleTypePublishConfirmForm form, boolean reshow, BindException errors) throws Exception
    {
        if (form.getDefaultValueSource().equals("Assay"))
            form.setDefaultValueSource("SampleType");

        if (_sampleType == null)
            return new HtmlView(HtmlString.unsafe("<span class='labkey-error'>Could not resolve the source Sample Type.</span>"));

        return super.getView(form, reshow, errors);
    }

    @Override
    protected SamplesSchema getUserSchema(SampleTypePublishConfirmForm form)
    {
        _sampleTypeSchema = (SamplesSchema) QueryService.get().getUserSchema(form.getUser(), getContainer(), SamplesSchema.SCHEMA_SAMPLES);
        return _sampleTypeSchema;
    }

    @Override
    protected QuerySettings getQuerySettings(SampleTypePublishConfirmForm form)
    {
        String queryName = form.getSampleType().getName();
        QuerySettings qs = new QuerySettings(form.getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, queryName);
        qs.setSchemaName(SamplesSchema.SCHEMA_NAME);
        qs.setQueryName(queryName);

        getUserSchema(form);

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
        return urlProvider(ExperimentUrls.class).getLinkToStudyConfirmURL(getContainer(), _sampleType).deleteParameters();
    }

    @Override
    protected FieldKey getObjectIdFieldKey(SampleTypePublishConfirmForm form)
    {
        return FieldKey.fromParts(ROW_ID);
    }

    // TODO
    private void intializeFieldKeys()
    {

    }

    @Override
    protected Map<PublishResultsQueryView.ExtraColFieldKeys, FieldKey> getAdditionalColumns(SampleTypePublishConfirmForm form)
    {
        String valueSource = form.getDefaultValueSource();
        DefaultValueSource defaultValueSource = DefaultValueSource.valueOf(valueSource);

        Map<PublishResultsQueryView.ExtraColFieldKeys, FieldKey> additionalCols = new HashMap<>();
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.ParticipantId, defaultValueSource.getParticipantIDFieldKey());
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.VisitId, defaultValueSource.getVisitIDFieldKey(TimepointType.VISIT));
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.Date, defaultValueSource.getVisitIDFieldKey(TimepointType.DATE));

        return additionalCols;
    }

    @Override
    protected Map<String, Object> getHiddenFormFields(SampleTypePublishConfirmForm form) // Rosaline temp note: very similar to Assay
    {
        Map<String, Object> fields = new HashMap<>();

        fields.put("rowId", _sampleType.getRowId());
        String returnURL = getViewContext().getRequest().getParameter(ActionURL.Param.returnUrl.name());
        if (returnURL == null)
        {
            returnURL = getViewContext().getActionURL().toString();
        }
        fields.put(ActionURL.Param.returnUrl.name(), returnURL);

        return fields;
    }

    // TODO
    @Override
    protected ActionURL copyToStudy(SampleTypePublishConfirmForm form, Container targetStudy, Map<Integer, PublishKey> publishData, List<String> publishErrors)
    {
        // Rosaline temp notes:
        // populates publishErrors should any be found
        // returns ActionURL of the new dataset that is created

        // should delegate to the sample type service
        return null;
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        // Rosaline TODO in later story: add help topic
        root.addChild("Sample Types", new ActionURL(ExperimentController.ListSampleTypesAction.class, getContainer()));
        if (_sampleType != null)
            root.addChild(_sampleType.getName(), urlProvider(ExperimentUrls.class).getShowSampleTypeURL(_sampleType)); // need ExpSampleType
        if (_targetStudyName != null)
            root.addChild("Copy to " + _targetStudyName + ": Verify Results");
    }
}
