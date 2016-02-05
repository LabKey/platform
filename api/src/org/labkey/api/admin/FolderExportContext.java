/*
 * Copyright (c) 2012-2016 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.folder.xml.FolderDocument;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public class FolderExportContext extends AbstractFolderContext
{
    private String _format = "new";
    private boolean _includeSubfolders = false;
    private boolean _removeProtected = false;
    private boolean _shiftDates = false;
    private boolean _alternateIds = false;
    private boolean _maskClinic = false;
    private Set<String> _viewIds;
    private Set<String> _reportIds;
    private Set<Integer> _listIds;

    public FolderExportContext(User user, Container c, Set<String> dataTypes, String format, LoggerGetter logger)
    {
        this(user, c, dataTypes, format, false, false, false, false, false, logger);
    }

    public FolderExportContext(User user, Container c, Set<String> dataTypes, String format, boolean includeSubfolders, boolean removeProtected, boolean shiftDates, boolean alternateIds, boolean maskClinic, LoggerGetter logger)
    {
        super(user, c, getFolderDocument(), dataTypes, logger, null);

        if (c.isDataspace() && dataTypes.contains("Study"))
            throw new IllegalStateException("Cannot export study from Dataspace folder.");
        _format = format;
        _includeSubfolders = includeSubfolders;
        _removeProtected = removeProtected;
        _shiftDates = shiftDates;
        _alternateIds = alternateIds;
        _maskClinic = maskClinic;
    }

    public String getFormat()
    {
        return _format;
    }

    public void setIncludeSubfolders(boolean includeSubfolders)
    {
        _includeSubfolders = includeSubfolders;
    }

    public boolean  isIncludeSubfolders()
    {
        return _includeSubfolders;
    }

    public boolean isRemoveProtected()
    {
        return _removeProtected;
    }

    public boolean isShiftDates()
    {
        return _shiftDates;
    }

    public boolean isAlternateIds()
    {
        return _alternateIds;
    }

    public boolean isMaskClinic()
    {
        return _maskClinic;
    }

    public static FolderDocument getFolderDocument()
    {
        FolderDocument doc = FolderDocument.Factory.newInstance();
        doc.addNewFolder();
        return doc;
    }

    public Set<String> getReportIds()
    {
        return _reportIds;
    }

    public void setReportIds(String[] reportIds)
    {
        _reportIds = new HashSet<>(Arrays.asList(reportIds));
    }

    public Set<String> getViewIds()
    {
        return _viewIds;
    }

    public void setViewIds(String[] viewIds)
    {
        _viewIds = new HashSet<>(Arrays.asList(viewIds));
    }

    public Set<Integer> getListIds()
    {
        return _listIds;
    }

    public void setListIds(Integer[] listIds)
    {
        _listIds = new HashSet<>(Arrays.asList(listIds));
    }
}
