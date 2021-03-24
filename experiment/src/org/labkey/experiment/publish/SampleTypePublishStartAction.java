package org.labkey.experiment.publish;

import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.query.QueryParam;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpPostRedirectView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewForm;
import org.labkey.api.study.publish.AbstractPublishStartAction;
import org.labkey.api.study.publish.PublishStartForm;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.labkey.api.util.PageFlowUtil.urlProvider;

@RequiresPermission(InsertPermission.class)
public class SampleTypePublishStartAction extends AbstractPublishStartAction<SampleTypePublishStartAction.SampleTypePublishStartForm>
{
    private List<Integer> _ids = new ArrayList<>();
    private List<Integer> _sampleTypeIds = new ArrayList<>();

    public static class SampleTypePublishStartForm extends ViewForm implements PublishStartForm
    {
        private String _dataRegionSelectionKey;
        private String _containerFilterName;
        private boolean _sampleTypeIds;

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

        // Rosaline Temp Note
        // The url you go to when you hit 'cancel'
        // For current case, the page showSampleType of some rowId: getShowSampleTypeURL
        // We shouldn't need it -- only gets triggered if _sampleTypeIds is true
        @Override
        public @Nullable ActionURL getReturnActionURL()
        {
            return super.getReturnActionURL();
        }
    }

    // Rosaline Temp Note
    // TODO: getLinkToStudyConfirmURL that's from SampleTypePublishConfirmAction
    @Override
    protected ActionURL getSuccessUrl(SampleTypePublishStartForm form)
    {
        return urlProvider(ExperimentUrls.class).getLinkToStudyConfirmURL(getContainer());
    }

    // Rosaline Temp Note
    // How we identify data -- row ids from sample type result domain
    // taking the form that gets passed from the startUrl which will either have the rowIds of the samples selected,
    // or the sample type id, and then the goal is to turn it into a list of object ids, that we pass back.
    // TODO look into getCheckboxIds()
    @Override
    protected List<Integer> getDataIDs(SampleTypePublishStartForm form)
    {
        return _ids;
    }

    // Rosaline Temp Note
    // list of sample types--materialSourceRowId or something
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

    // Rosaline Temp Note
    // TODO
    @Override
    public void addNavTrail(NavTree root)
    {

    }

//    Rosaline Temp Note
//    TODO: May want to initialize some things in the form?
//    Note that a lot of code below is taken from assay. We may want to re-modularize
    @Override
    public ModelAndView getView(SampleTypePublishStartForm form, BindException errors)
    {

        // Rosaline Temp Note: Do we also need to do this? Check it out
        // If the TargetStudy column is on the result domain, redirect past the choose target study page directly to the confirm page.
        List<Integer> ids = getDataIDs(form);


//        if (true) { // TODO
//            Collection<Pair<String, String>> inputs = new ArrayList<>();
//            inputs.add(Pair.of(QueryParam.containerFilterName.name(), form.getContainerFilterName()));
//            if (form.getReturnUrl() != null)
//                inputs.add(Pair.of(ActionURL.Param.returnUrl.name(), form.getReturnUrl().toString()));
//            inputs.add(Pair.of(DataRegionSelection.DATA_REGION_SELECTION_KEY, form.getDataRegionSelectionKey()));
//            for (Integer id : ids)
//                inputs.add(Pair.of(DataRegion.SELECT_CHECKBOX_NAME, id.toString()));
//
//            // Copy url parameters to hidden inputs
//            ActionURL url = urlProvider(ExperimentUrls.class).getLinkToStudyConfirmURL(getContainer());
//            for (Pair<String, String> parameter : url.getParameters())
//                inputs.add(parameter);
//
//            url.deleteParameters();
//            getPageConfig().setTemplate(PageConfig.Template.None);
//            return new HttpPostRedirectView(url.toString(), inputs);
//        }


        return super.getView(form, errors);
    }
}
