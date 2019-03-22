/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
import org.labkey.api.security.User;
import org.labkey.api.webdav.WebdavResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 3:28:31 PM
 */
public interface VirtualFile extends AutoCloseable
{
    /** PrintWriter that always encodes as UTF-8 */
    PrintWriter getPrintWriter(String path) throws IOException;
    /** Meant for binary data... if used with character data you must encode as UTF-8 yourself */
    OutputStream getOutputStream(String filename) throws IOException;
    void saveXmlBean(String filename, XmlObject doc) throws IOException;
    /** Recursively exports the contents of the resource to this directory */
    void saveWebdavTree(WebdavResource resource, User user) throws IOException;
    XmlObject getXmlBean(String filename) throws IOException;
    InputStream getInputStream(String filename) throws IOException;
    String getRelativePath(String filename);
    /** @return all of the file children */
    List<String> list();
    /** @return all of the directory children */
    List<String> listDirs();
    boolean delete(String filename);

    VirtualFile createZipArchive(String name) throws IOException;
    VirtualFile getDir(String path);
    String makeLegalName(String name);
    String getLocation();
    void close() throws IOException;
}
