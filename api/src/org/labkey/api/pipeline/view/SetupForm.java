/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

package org.labkey.api.pipeline.view;

import org.labkey.api.data.Container;
import org.labkey.api.pipeline.GlobusKeyPair;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.view.ActionURL;
import org.springframework.validation.BindException;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Dec 14, 2009
 */
public class SetupForm
{
    private String _path;
    private String _keyPassword;
    private boolean _uploadNewGlobusKeys;
    private String _confirmMessage;
    private String _strValue;
    private ActionURL _doneURL;
    private GlobusKeyPair _globusKeyPair;
    private BindException _errors;
    private boolean _pipelineRootForm;
    private String _pipelineRootOption;
    private boolean _searchable;

    public String getConfirmMessage()
    {
        return _confirmMessage;
    }

    public void setConfirmMessage(String confirmMessage)
    {
        _confirmMessage = confirmMessage;
    }

    public String getStrValue()
    {
        return _strValue;
    }

    public void setStrValue(String strValue)
    {
        _strValue = strValue;
    }

    public ActionURL getDoneURL()
    {
        return _doneURL;
    }

    public void setDoneURL(ActionURL doneURL)
    {
        _doneURL = doneURL;
    }

    public GlobusKeyPair getGlobusKeyPair()
    {
        return _globusKeyPair;
    }

    public void setGlobusKeyPair(GlobusKeyPair globusKeyPair)
    {
        _globusKeyPair = globusKeyPair;
    }

    public String getPath()
    {
        return _path;
    }

    public void setPath(String path)
    {
        _path = path;
    }

    public BindException getErrors()
    {
        return _errors;
    }

    public void setErrors(BindException errors)
    {
        _errors = errors;
    }

    public String getKeyPassword()
    {

        return _keyPassword;
    }

    public void setKeyPassword(String keyPassword)
    {
        _keyPassword = keyPassword;
    }

    public boolean isUploadNewGlobusKeys()
    {
        return _uploadNewGlobusKeys;
    }

    public void setUploadNewGlobusKeys(boolean uploadNewGlobusKeys)
    {
        _uploadNewGlobusKeys = uploadNewGlobusKeys;
    }

    public boolean isPipelineRootForm()
    {
        return _pipelineRootForm;
    }

    public void setPipelineRootForm(boolean pipelineRootForm)
    {
        _pipelineRootForm = pipelineRootForm;
    }

    public String getPipelineRootOption()
    {
        return _pipelineRootOption;
    }

    public void setPipelineRootOption(String pipelineRootOption)
    {
        _pipelineRootOption = pipelineRootOption;
    }

    public boolean isSearchable()
    {
        return _searchable;
    }

    public void setSearchable(boolean searchable)
    {
        _searchable = searchable;
    }

    public boolean hasSiteDefaultPipelineRoot()
    {
        return "siteDefault".equals(getPipelineRootOption());
    }

    public static SetupForm init(Container c)
    {
        SetupForm form = new SetupForm();

        if (PipelineService.get().hasSiteDefaultRoot(c))
            form.setPipelineRootOption("siteDefault");
        else
            form.setPipelineRootOption("projectSpecified");
        
        PipeRoot root = getPipelineRoot(c);
        if (root != null)
        {
            File rootFile = root.getRootPath();
            if (rootFile != null)
                form.setStrValue(rootFile.getPath());

            form.setGlobusKeyPair(root.getGlobusKeyPair());
            form.setSearchable(root.isSearchable());
        }
        return form;
    }

    public static PipeRoot getPipelineRoot(Container c)
    {
        PipeRoot p = PipelineService.get().findPipelineRoot(c);
        if (p != null && p.getContainer() != null && p.getContainer().getId().equals(c.getId()))
            return p;

        return null;
    }
}
