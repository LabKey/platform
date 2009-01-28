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
package org.labkey.api.module;

import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.Portal;
import org.labkey.api.util.DOMUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

/*
* User: Dave
* Date: Jan 26, 2009
* Time: 11:16:03 AM
*/

/**
 * A factory for web parts defined as simple XML files in a module
 */
public class SimpleWebPartFactory extends BaseWebPartFactory
{
    public static final String FILE_EXTENSION = ".webpart.xml";

    //can be used to select all webpart files in a directory
    public static final FilenameFilter webPartFileFilter = new FilenameFilter(){
        public boolean accept(File dir, String name)
        {
            return name.toLowerCase().endsWith(FILE_EXTENSION);
        }
    };

    private File _webPartFile;
    private long _lastModified = 0;
    private Module _module;
    private String _viewName;

    public SimpleWebPartFactory(Module module, File webPartFile)
    {
        super(getNameFromFile(webPartFile));
        _module = module;
        loadDefinition(webPartFile);
        _webPartFile = webPartFile;
        _lastModified = webPartFile.lastModified();
    }

    protected static String getNameFromFile(File webPartFile)
    {
        String name = webPartFile.getName();
        return name.substring(0, name.length() - FILE_EXTENSION.length());
    }

    protected void loadDefinition(File webPartFile)
    {
        try
        {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(false);
            DocumentBuilder db = dbf.newDocumentBuilder();

            Document doc = db.parse(webPartFile);
            if(null == doc || !"webpart".equalsIgnoreCase(doc.getDocumentElement().getNodeName()))
                throw new RuntimeException("Webpart definition file " + webPartFile.getAbsolutePath() + " does not contain a root 'webpart' element!");

            //optional title attribute for overriding name
            String title = DOMUtil.getAttributeValue(doc.getDocumentElement(), "title");
            if(null != title)
                setName(title);

            Node viewElem = DOMUtil.getFirstChildNodeWithName(doc.getDocumentElement(), "view");
            if(null == viewElem)
                throw new RuntimeException("Webpart definition file " + webPartFile.getAbsolutePath() + " does not contain a 'view' element!");

            //for now, view element may only contain a name attribute naming a view in the views directory
            _viewName = DOMUtil.getAttributeValue(viewElem, "name");
            if(null == _viewName)
                throw new RuntimeException("No view name specified in webpart definition file " + webPartFile.getAbsolutePath());
        }
        catch(IOException e)
        {
            throw new RuntimeException(e);
        }
        catch(SAXException e)
        {
            throw new RuntimeException(e);
        }
        catch(ParserConfigurationException e)
        {
            throw new RuntimeException(e);
        }
    }

    public String getViewName()
    {
        return _viewName;
    }

    public Module getModule()
    {
        return _module;
    }

    public boolean isStale()
    {
        return _webPartFile.lastModified() != _lastModified;
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        if(isStale())
            loadDefinition(_webPartFile);

        return SimpleAction.getModuleHtmlView(getModule(), getViewName());
    }
}