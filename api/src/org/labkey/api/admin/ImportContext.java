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

import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Activity;
import org.labkey.api.data.PHI;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.VirtualFile;

import java.util.Set;

/**
 * Captures the values for options that can be set when importing or exporting a folder archive.
 * User: jeckels
 * Date: Jan 18, 2012
 */
public interface ImportContext<XmlType extends XmlObject> extends ContainerUser
{
    XmlType getXml() throws ImportException;
    VirtualFile getDir(String xmlNodeName) throws ImportException;
    Logger getLogger();
    LoggerGetter getLoggerGetter();
    Set<String> getDataTypes();
    String getFormat();
    boolean isIncludeSubfolders();
    PHI getPhiLevel();
    boolean isShiftDates();
    boolean isAlternateIds();
    boolean isMaskClinic();
    Double getArchiveVersion();
    boolean isSkipQueryValidation();
    boolean isCreateSharedDatasets();
    boolean isFailForUndefinedVisits();
    boolean isDataTypeSelected(String dataType);
    Activity getActivity();
    boolean isAddExportComment();

    // At some point, we'll likely add an advanced option in the Import UI to allow a user to choose an audit behavior type
    // during import, but for current purposes, we are implementing this only for certain folder types.
    @Nullable
    default AuditBehaviorType getAuditBehaviorType() throws Exception
    {
        return null;
    }

    // Used to determine the level of permissions the user needs to have in order to export a subfolder.
    default Class<? extends Permission> getSubfolderPermission()
    {
        return FolderExportPermission.class;
    }

    // These methods let writers add and get module-specific context information
    <K extends ImportContext> void addContext(Class<K> contextClass, K context);
    <K extends ImportContext> K getContext(Class<K> contextClass);
}
