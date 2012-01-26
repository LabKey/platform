/*
 * Copyright (c) 2012 LabKey Corporation
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

import java.io.File;

/**
 * User: jeckels
 * Date: Jan 18, 2012
 */
public interface ImportContext<XmlType extends XmlObject> extends ContainerUser
{
    public XmlType getXml() throws ImportException;
    public File getDir(String xmlNodeName) throws ImportException;
    public Logger getLogger();
}
