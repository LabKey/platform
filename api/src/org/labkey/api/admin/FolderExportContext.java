/*
 * Copyright (c) 2012-2018 LabKey Corporation
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
import org.labkey.api.data.PHI;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
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
    private final String _format;
    private final PHI _phiLevel;
    private final boolean _shiftDates;
    private final boolean _alternateIds;
    private final boolean _maskClinic;

    private boolean _includeFolderXml;
    private boolean _includeSubfolders;
    private Set<String> _viewIds;
    private Set<String> _reportIds;
    private Set<Integer> _listIds;
    private Set<String> _queryKeys;

    public FolderExportContext(User user, Container c, Set<String> dataTypes, String format, LoggerGetter logger)
    {
        this(user, c, dataTypes, format, false, PHI.Restricted, false, false, false, logger);
    }

    public FolderExportContext(User user, Container c, Set<String> dataTypes, String format, boolean includeSubfolders, PHI phiLevel, boolean shiftDates, boolean alternateIds, boolean maskClinic, LoggerGetter logger)
    {
        super(user, c, getFolderDocument(), dataTypes, logger, null);

        // If the container is a Dataspace folder type and we are requesting exporting the study objects,
        // verify that the user is allowed to export the study for this Dataspace configuration.
        if (c.isDataspace() && dataTypes.contains("Study"))
        {
            Study study = StudyService.get().getStudy(c);
            if (study != null && !study.allowExport(getUser()))
                throw new IllegalStateException("Cannot export study from this Dataspace folder.");
        }

        _format = format;
        _includeFolderXml = true;
        _includeSubfolders = includeSubfolders;
        _phiLevel = phiLevel;
        _shiftDates = shiftDates;
        _alternateIds = alternateIds;
        _maskClinic = maskClinic;
    }

    @Override
    public String getFormat()
    {
        return _format;
    }

    public void setIncludeFolderXml(boolean includeFolderXml)
    {
        _includeFolderXml = includeFolderXml;
    }

    public boolean isIncludeFolderXml()
    {
        return _includeFolderXml;
    }

    @Override
    public void setIncludeSubfolders(boolean includeSubfolders)
    {
        _includeSubfolders = includeSubfolders;
    }

    @Override
    public boolean isIncludeSubfolders()
    {
        return _includeSubfolders;
    }

    @Override
    public PHI getPhiLevel()
    {
        return _phiLevel;
    }

    @Override
    public boolean isShiftDates()
    {
        return _shiftDates;
    }

    @Override
    public boolean isAlternateIds()
    {
        return _alternateIds;
    }

    @Override
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

    public Set<String> getQueryKeys()
    {
        return _queryKeys;
    }

    public void setQueryKeys(String[] queryKeys)
    {
        _queryKeys = new HashSet<>(Arrays.asList(queryKeys));
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
