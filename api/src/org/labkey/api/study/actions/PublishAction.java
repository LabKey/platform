package org.labkey.api.study.actions;

import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:09:28 PM
*/
@RequiresPermission(ACL.PERM_INSERT)
public class PublishAction extends BaseAssayAction<PublishAction.PublishForm>
{
    public static class PublishForm extends ProtocolIdForm implements DataRegionSelection.DataSelectionKeyForm
    {
        private String _viewType;
        private String _dataRegionSelectionKey;

        public String getViewType()
        {
            return _viewType;
        }

        public void setViewType(String viewType)
        {
            _viewType = viewType;
        }

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            _dataRegionSelectionKey = dataRegionSelectionKey;
        }
    }

    public static class PublishBean
    {
        private List<Integer> _ids;
        private AssayProvider _provider;
        private ExpProtocol _protocol;
        private Set<Container> _studies;
        private boolean _nullStudies;
        private String _dataRegionSelectionKey;

        public PublishBean(AssayProvider provider, ExpProtocol protocol,
                           List<Integer> ids, String dataRegionSelectionKey,
                           Set<Container> studies, boolean nullStudies)
        {
            _provider = provider;
            _protocol = protocol;
            _studies = studies;
            _nullStudies = nullStudies;
            _ids = ids;
            _dataRegionSelectionKey = dataRegionSelectionKey;
        }

        public List<Integer> getIds()
        {
            return _ids;
        }

        public Set<Container> getStudies()
        {
            return _studies;
        }

        public boolean isNullStudies()
        {
            return _nullStudies;
        }

        public AssayProvider getProvider()
        {
            return _provider;
        }

        public ExpProtocol getProtocol()
        {
            return _protocol;
        }

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }
    }

    public ModelAndView getView(PublishForm publishForm, BindException errors) throws Exception
    {
        ExpProtocol protocol = getProtocol(publishForm);
        AssayProvider provider = AssayService.get().getProvider(protocol);
        List<Integer> ids = getCheckboxIds(false);
        Set<Container> containers = new HashSet<Container>();
        boolean nullsFound = false;
        for (Integer id : ids)
        {
            Container studyContainer = provider.getAssociatedStudyContainer(protocol, id);
            if (studyContainer == null)
                nullsFound = true;
            else
                containers.add(studyContainer);
        }

        return new JspView<PublishBean>("/org/labkey/study/assay/view/publishChooseStudy.jsp",
                new PublishBean(provider, protocol, ids, publishForm.getDataRegionSelectionKey(), containers, nullsFound));
    }

    public NavTree appendNavTrail(NavTree root)
    {
        return super.appendNavTrail(root).addChild("Copy to Study: Choose Target");
    }
}
