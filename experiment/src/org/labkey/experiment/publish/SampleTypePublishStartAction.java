package org.labkey.experiment.publish;

import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewForm;
import org.labkey.api.study.publish.AbstractPublishStartAction;
import org.labkey.api.study.publish.PublishStartForm;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static org.labkey.api.util.PageFlowUtil.urlProvider;

@RequiresPermission(InsertPermission.class)
public class SampleTypePublishStartAction extends AbstractPublishStartAction<SampleTypePublishStartAction.SampleTypePublishStartForm>
{
    private List<Integer> _ids = new ArrayList<>();
    private List<Integer> _sampleTypeIds = new ArrayList<>();
    private ExpSampleType _sampleType;

    public static class SampleTypePublishStartForm extends ViewForm implements PublishStartForm
    {
        private String _dataRegionSelectionKey;
        private String _containerFilterName;
        private boolean _sampleTypeIds;
        private Integer _rowId;

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

        // TODO: double-check what these do on Assay
        public String getContainerFilterName()
        {
            return _containerFilterName;
        }

        public void setContainerFilterName(String containerFilterName)
        {
            _containerFilterName = containerFilterName;
        }

        public boolean isSampleTypeIds()
        {
            return _sampleTypeIds;
        }

        public void setSampleTypeIds(boolean sampleTypeIds)
        {
            _sampleTypeIds = sampleTypeIds;
        }

        public Integer getRowId()
        {
            return _rowId;
        }

        public void setRowId(Integer rowId)
        {
            _rowId = rowId;
        }

        // Rosaline Temp Note
        // For current case, the page showSampleType of some rowId: getShowSampleTypeURL
        // We shouldn't need it -- only gets triggered if _sampleTypeIds is true
        @Override
        public @Nullable ActionURL getReturnActionURL()
        {
            return super.getReturnActionURL();
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
    protected ActionURL getSuccessUrl(SampleTypePublishStartForm form)
    {
        ActionURL url = urlProvider(ExperimentUrls.class).getLinkToStudyConfirmURL(getContainer(), _sampleType);
        url.addParameter("rowId", form.getRowId());
        return url;
    }

    // Rosaline Temp Note
    // How we identify data -- row ids from sample type result domain
    // taking the form that gets passed from the startUrl which will either have the rowIds of the samples selected,
    // or the sample type id, and then the goal is to turn it into a list of object ids, that we pass back.
    @Override
    protected List<Integer> getDataIDs(SampleTypePublishStartForm form)
    {
        // Rosaline TODO in later story: Support  SampleType-level links
        if (_ids.isEmpty())
        {
            _ids = getCheckboxIds(getViewContext());
            _sampleType = SampleTypeService.get().getSampleType(form.getContainer(), form.getRowId());
        }
        return _ids;
    }

    // Rosaline Temp Note
    // list of sample types
    @Override
    protected List<Integer> getBatchIds()
    {
        return _sampleTypeIds;
    }

    // Rosaline Temp Note
    // Double-check this
    @Override
    protected String getBatchNoun()
    {
        return "Sample Type";
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        // Rosaline TODO in later story: add help topic
        root.addChild("Sample Types", new ActionURL(ExperimentController.ListSampleTypesAction.class, getContainer()));
        root.addChild(_sampleType.getName(), urlProvider(ExperimentUrls.class).getShowSampleTypeURL(_sampleType)); // need ExpSampleType
        root.addChild("Link to Study: Choose Target");
    }

    @Override
    public ModelAndView getView(SampleTypePublishStartForm form, BindException errors)
    {
        // Rosaline Temp Note: Do we also need to do this line below from assay? Check it out
        // If the TargetStudy column is on the result domain, redirect past the choose target study page directly to the confirm page.

        getDataIDs(form);

        return super.getView(form, errors);
    }
}
