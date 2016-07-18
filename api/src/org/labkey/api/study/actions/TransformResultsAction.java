/*
 * Copyright (c) 2015-2016 LabKey Corporation
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

import org.labkey.api.qc.TsvDataExchangeHandler;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URIUtil;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.File;

/**
 * Created by Marty on 11/16/2015.
 */

@RequiresPermission(InsertPermission.class)
public class TransformResultsAction extends BaseAssayAction<TransformResultsAction.TransformResultsForm>
{

    @Override
    public ModelAndView getView(TransformResultsForm form, BindException errors) throws Exception
    {
        File transformDir = TsvDataExchangeHandler.getWorkingDirectory(form, getUser());
        if (null != transformDir)
        {
            File downloadFile = new File(transformDir, form.getName());
            if(URIUtil.isDescendant(FileUtil.resolveFile(new File(transformDir.getParent())).toURI(), FileUtil.resolveFile(downloadFile).toURI()))
            {
                HttpServletResponse response = getViewContext().getResponse();
                PageFlowUtil.streamFile(response, downloadFile, true);
            }
        }

        return null;
    }

    public static class TransformResultsForm extends ProtocolIdForm
    {
        private String _name;
        private String _uploadAttemptID;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String getUploadAttemptID()
        {
            return _uploadAttemptID;
        }

        public void setUploadAttemptID(String uploadAttemptId)
        {
            _uploadAttemptID = uploadAttemptId;
        }
    }
}
