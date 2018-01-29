/*
 * Copyright (c) 2012-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlObject;
import org.labkey.api.data.Activity;
import org.labkey.api.data.PHI;
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

    // These methods let writers add and get module-specific context information
    <K extends ImportContext> void addContext(Class<K> contextClass, K context);
    <K extends ImportContext> K getContext(Class<K> contextClass);
}
