/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
import org.labkey.api.webdav.WebdavResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 3:28:31 PM
 */
public interface VirtualFile extends AutoCloseable
{
    /** PrintWriter that always encodes as UTF-8 */
    public PrintWriter getPrintWriter(String path) throws IOException;
    /** Meant for binary data... if used with character data you must encode as UTF-8 yourself */
    public OutputStream getOutputStream(String filename) throws IOException;
    public void saveXmlBean(String filename, XmlObject doc) throws IOException;
    /** Recursively exports the contents of the resource to this directory */
    public void saveWebdavTree(WebdavResource resource) throws IOException;
    public XmlObject getXmlBean(String filename) throws IOException;
    public InputStream getInputStream(String filename) throws IOException;
    public String getRelativePath(String filename);
    public String[] list();
    public String[] listDirs();
    public boolean delete(String filename);

    public VirtualFile createZipArchive(String name) throws IOException;
    public VirtualFile getDir(String path);
    public String makeLegalName(String name);
    public String getLocation();
    public void close() throws IOException;
}
