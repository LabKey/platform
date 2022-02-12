package org.labkey.study.controllers.publish;

import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.publish.AbstractPublishStartAction;
import org.labkey.api.study.publish.PublishStartForm;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewForm;
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

        public String getContainerFilterName()
        {
            return _containerFilterName;
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
        return urlProvider(StudyUrls.class).getLinkToStudyConfirmURL(getContainer(), _sampleType);
    }

    @Override
    protected List<Integer> getDataIDs(SampleTypePublishStartForm form)
    {
        // Deferred: Support SampleType-level links
        if (_ids.isEmpty() && !form.isSampleTypeIds() && null != form.getRowId())
        {
            _ids = getCheckboxIds(getViewContext());
            _sampleType = SampleTypeService.get().getSampleType(form.getContainer(), form.getUser(), form.getRowId());
            form.setAutoLinkEnabled(_sampleType != null && _sampleType.getAutoLinkCategory() != null);
        }
        return _ids;
    }

    @Override
    protected List<Integer> getBatchIds()
    {
        return _sampleTypeIds;
    }

    @Override
    protected String getBatchNoun()
    {
        return "Sample Type";
    }

    @Override
    public ModelAndView getView(SampleTypePublishStartForm form, BindException errors)
    {
        getDataIDs(form);
        return super.getView(form, errors);
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        setHelpTopic("linkSampleData");
        root.addChild("Sample Types", ExperimentUrls.get().getShowSampleTypeListURL(getContainer()));
        if (_sampleType != null)
            root.addChild(_sampleType.getName(), urlProvider(ExperimentUrls.class).getShowSampleTypeURL(_sampleType));
        root.addChild("Link to Study: Choose Target");
    }
}
