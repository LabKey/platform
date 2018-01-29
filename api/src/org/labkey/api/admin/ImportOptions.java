/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.api.admin;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Activity;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;

/**
 * Settings to control behavior during the import of a folder or study.
 * User: klum
 * Date: 10/10/13
 */
public class ImportOptions
{
    private boolean _skipQueryValidation;
    private boolean _createSharedDatasets;
    private boolean _advancedImportOptions;
    private boolean _failForUndefinedVisits;
    private boolean _includeSubfolders = true; // default to true, unless explicitly disabled (i.e. advanced import to multiple folders option)
    private String _containerId;
    private Integer _userId = null;
    private Collection<String> _messages = new LinkedList<>();
    private Set<String> _dataTypes;
    private Activity _activity;

    public ImportOptions(String containerId, @Nullable Integer userId)
    {
        _containerId = containerId;
        _userId = userId;
    }

    public boolean isSkipQueryValidation()
    {
        return _skipQueryValidation;
    }

    public void setSkipQueryValidation(boolean skipQueryValidation)
    {
        _skipQueryValidation = skipQueryValidation;
    }

    public String getContainerId()
    {
        return _containerId;
    }

    public void setContainerId(String containerId)
    {
        _containerId = containerId;
    }

    public boolean isCreateSharedDatasets()
    {
        return _createSharedDatasets;
    }

    public void setCreateSharedDatasets(boolean createSharedDatasets)
    {
        _createSharedDatasets = createSharedDatasets;
    }

    public boolean isAdvancedImportOptions()
    {
        return _advancedImportOptions;
    }

    public void setAdvancedImportOptions(boolean advancedImportOptions)
    {
        _advancedImportOptions = advancedImportOptions;
    }

    public boolean isFailForUndefinedVisits()
    {
        return _failForUndefinedVisits;
    }

    public void setFailForUndefinedVisits(boolean failForUndefinedVisits)
    {
        _failForUndefinedVisits = failForUndefinedVisits;
    }

    public boolean isIncludeSubfolders()
    {
        return _includeSubfolders;
    }

    public void setIncludeSubfolders(boolean includeSubfolders)
    {
        _includeSubfolders = includeSubfolders;
    }

    public @Nullable User getUser()
    {
        return null != _userId ? UserManager.getUser(_userId) : null;
    }

    public void setUser(User user)
    {
        _userId = user.getUserId();
    }

    public void addMessage(String message)
    {
        _messages.add(message);
    }

    public Collection<String> getMessages()
    {
        return _messages;
    }

    public Set<String> getDataTypes()
    {
        return _dataTypes;
    }

    public void setDataTypes(Set<String> dataTypes)
    {
        _dataTypes = dataTypes;
    }

    public Activity getActivity()
    {
        return _activity;
    }

    public void setActivity(Activity activity)
    {
        _activity = activity;
    }
}
