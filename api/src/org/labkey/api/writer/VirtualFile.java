/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.writer;

import org.apache.xmlbeans.XmlObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 3:28:31 PM
 */
public interface VirtualFile
{
    public PrintWriter getPrintWriter(String path) throws IOException;
    public OutputStream getOutputStream(String filename) throws IOException;
    public void saveXmlBean(String filename, XmlObject doc) throws IOException;
    public Archive createZipArchive(String name) throws IOException;
    public VirtualFile getDir(String path);
    public String makeLegalName(String name);
    public String getLocation();
}
