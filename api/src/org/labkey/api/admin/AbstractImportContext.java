/*
 * Copyright (c) 2011 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;

/**
 * User: jeckels
 * Date: Jan 18, 2012
 */
public abstract class AbstractImportContext<XmlRoot extends XmlObject, XmlDocument extends XmlObject> implements ImportContext<XmlRoot>
{
    private final User _user;
    private final Container _c;
    private final Logger _logger;
    @Nullable private final File _root;

    private transient XmlDocument _xmlDocument;

    private boolean _locked = false;

    protected AbstractImportContext(User user, Container c, XmlDocument document, Logger logger, @Nullable File root)
    {
        _user = user;
        _c = c;
        _logger = logger;
        _xmlDocument = document;
        _root = root;
    }

    public User getUser()
    {
        return _user;
    }

    public Container getContainer()
    {
        return _c;
    }

    public File getDir(String xmlNodeName) throws ImportException
    {
        if (_root == null)
            throw new IllegalStateException("Not supported during export");

        NodeList childNodes = getXml().getDomNode().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++)
        {
            Node childNode = childNodes.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE && childNode.getLocalName().equalsIgnoreCase(xmlNodeName))
            {
                String dirName = ((Element)childNode).getAttribute("dir");

                File dir = null != dirName ? new File(_root, dirName) : _root;

                if (!dir.exists())
                    throw new ImportException("Main import file refers to a directory that does not exist: " + ImportException.getRelativePath(_root, dir));

                if (!dir.isDirectory())
                    throw new ImportException("Main import file refers to " + ImportException.getRelativePath(_root, dir) + ": expected a directory but found a file");

                return dir;
            }
        }

        return null;
    }

    public Logger getLogger()
    {
        return _logger;
    }

    public void lockDocument()
    {
        _locked = true;
    }

    public synchronized XmlDocument getDocument() throws ImportException
    {
        if (_locked)
            throw new IllegalStateException("Can't access document after XML has been written");

        return _xmlDocument;
    }

    protected final synchronized void setDocument(XmlDocument folderDoc)
    {
        _xmlDocument = folderDoc;
    }

}
