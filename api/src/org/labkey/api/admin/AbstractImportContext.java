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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Activity;
import org.labkey.api.data.Container;
import org.labkey.api.data.PHI;
import org.labkey.api.security.User;
import org.labkey.api.writer.VirtualFile;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: Jan 18, 2012
 */
public abstract class AbstractImportContext<XmlRoot extends XmlObject, XmlDocument extends XmlObject> implements ImportContext<XmlRoot>
{
    private final Set<String> _dataTypes;
    private final User _user;
    private final Container _c;
    private final LoggerGetter _logger;
    private final @Nullable VirtualFile _root;
    private final Map<Class<? extends ImportContext>, ImportContext> _contextMap = new HashMap<>();
    private boolean _skipQueryValidation;
    private boolean _createSharedDatasets;
    private boolean _failForUndefinedVisits;
    private boolean _includeSubfolders = true; // default to true, unless explicitly disabled (i.e. advanced import to multiple folders option)
    private Activity _activity;

    private transient XmlDocument _xmlDocument;

    private boolean _locked = false;

    protected AbstractImportContext(User user, Container c, XmlDocument document, Set<String> dataTypes, LoggerGetter logger, @Nullable VirtualFile root)
    {
        _user = user;
        _c = c;
        _dataTypes = dataTypes;
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

    public VirtualFile getDir(String xmlNodeName) throws ImportException
    {
        if (_root == null)
            throw new IllegalStateException("Not supported during export");

        NodeList childNodes = getXml().getDomNode().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++)
        {
            Node childNode = childNodes.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE && childNode.getLocalName().equalsIgnoreCase(xmlNodeName) && ((Element)childNode).hasAttribute("dir"))
            {
                String dirName = ((Element)childNode).getAttribute("dir");

                VirtualFile dir = null != dirName ? _root.getDir(dirName) : null;

                if (null == dir)
                    throw new ImportException("Main import file refers to a directory that does not exist: " + _root.getRelativePath(dirName));

                return dir;
            }
        }

        return null;
    }

    @Override
    public Logger getLogger()
    {
        return _logger.getLogger();
    }

    @Override
    public LoggerGetter getLoggerGetter()
    {
        return _logger;
    }

    public VirtualFile getRoot()
    {
        if (_root == null)
            throw new IllegalStateException("Not supported during export");        

        return _root;
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

    protected final synchronized void setDocument(XmlDocument doc)
    {
        _xmlDocument = doc;
    }

    @Override
    public Set<String> getDataTypes()
    {
        return _dataTypes;
    }

    @Override
    public String getFormat()
    {
        return "new";
    }

    @Override
    public boolean isIncludeSubfolders()
    {
        return _includeSubfolders;
    }

    public void setIncludeSubfolders(boolean includeSubfolders)
    {
        _includeSubfolders = includeSubfolders;
    }

    @Override
    public PHI getPhiLevel()
    {
        return PHI.NotPHI;
    }

    @Override
    public boolean isShiftDates()
    {
        return false;
    }

    @Override
    public boolean isAlternateIds()
    {
        return false;
    }

    @Override
    public boolean isMaskClinic()
    {
        return false;
    }

    @Override
    public Double getArchiveVersion()
    {
        return null;
    }

    @Override
    public <K extends ImportContext> void addContext(Class<K> contextClass, K context)
    {
        _contextMap.put(contextClass, context);
    }

    @Override
    public <K extends ImportContext> K getContext(Class<K> contextClass)
    {
        //noinspection unchecked
        return (K)_contextMap.get(contextClass);
    }

    @Override
    public boolean isSkipQueryValidation()
    {
        return _skipQueryValidation;
    }

    public void setSkipQueryValidation(boolean skipQueryValidation)
    {
        _skipQueryValidation = skipQueryValidation;
    }

    @Override
    public boolean isCreateSharedDatasets()
    {
        return _createSharedDatasets;
    }

    public void setCreateSharedDatasets(boolean createSharedDatasets)
    {
        _createSharedDatasets = createSharedDatasets;
    }

    @Override
    public boolean isFailForUndefinedVisits()
    {
        return _failForUndefinedVisits;
    }

    public void setFailForUndefinedVisits(boolean failForUndefinedVisits)
    {
        _failForUndefinedVisits = failForUndefinedVisits;
    }

    @Override
    public boolean isDataTypeSelected(String dataType)
    {
        return _dataTypes == null || dataType == null || _dataTypes.contains(dataType);
    }

    public Activity getActivity()
    {
        return _activity;
    }

    public void setActivity(Activity activity)
    {
        _activity = activity;
    }
}
