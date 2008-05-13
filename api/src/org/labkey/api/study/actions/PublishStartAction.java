/*
 * Copyright (c) 2007-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
public class PublishStartAction extends BaseAssayAction<PublishStartAction.PublishForm>
{
    private ExpProtocol _protocol;

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
        private boolean _insufficientPermissions;
        private String _dataRegionSelectionKey;

        public PublishBean(AssayProvider provider, ExpProtocol protocol,
                           List<Integer> ids, String dataRegionSelectionKey,
                           Set<Container> studies, boolean nullStudies, boolean insufficientPermissions)
        {
            _insufficientPermissions = insufficientPermissions;
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
        
        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
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

        public boolean isInsufficientPermissions()
        {
            return _insufficientPermissions;
        }
    }

    public ModelAndView getView(PublishForm publishForm, BindException errors) throws Exception
    {
        _protocol = getProtocol(publishForm);
        AssayProvider provider = AssayService.get().getProvider(_protocol);
        List<Integer> ids = getCheckboxIds(false);
        Set<Container> containers = new HashSet<Container>();
        boolean nullsFound = false;
        boolean insufficientPermissions = false;
        for (Integer id : ids)
        {
            Container studyContainer = provider.getAssociatedStudyContainer(_protocol, id);
            if (studyContainer == null)
                nullsFound = true;
            else
            {
                if (!studyContainer.hasPermission(getViewContext().getUser(), ACL.PERM_INSERT))
                    insufficientPermissions = true;
                containers.add(studyContainer);
            }
        }

        return new JspView<PublishBean>("/org/labkey/study/assay/view/publishChooseStudy.jsp",
                new PublishBean(provider, _protocol, ids, publishForm.getDataRegionSelectionKey(), containers, nullsFound, insufficientPermissions));
    }

    public NavTree appendNavTrail(NavTree root)
    {
        NavTree result = super.appendNavTrail(root);
        result.addChild(_protocol.getName(), AssayService.get().getAssayRunsURL(getContainer(), _protocol));
        result.addChild("Copy to Study: " + _protocol.getName() + ": Choose Study");
        return result;
    }
}
