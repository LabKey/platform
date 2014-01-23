/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.VirtualFile;

import java.util.Set;

/**
 * User: jeckels
 * Date: Jan 18, 2012
 */
public interface ImportContext<XmlType extends XmlObject> extends ContainerUser
{
    public XmlType getXml() throws ImportException;
    public VirtualFile getDir(String xmlNodeName) throws ImportException;
    public Logger getLogger();
    public LoggerGetter getLoggerGetter();
    public Set<String> getDataTypes();
    public String getFormat();
    public boolean isIncludeSubfolders();
    public boolean isRemoveProtected();
    public boolean isShiftDates();
    public boolean isAlternateIds();
    public boolean isMaskClinic();
    public Double getArchiveVersion();
    public boolean isSkipQueryValidation();
    public boolean isCreateSharedDatasets();

    // These methods let writers add and get module-specific context information
    public <K extends ImportContext> void addContext(Class<K> contextClass, K context);
    public <K extends ImportContext> K getContext(Class<K> contextClass);
}
