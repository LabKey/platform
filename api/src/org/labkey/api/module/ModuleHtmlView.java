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

import org.labkey.api.view.HtmlView;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.util.DOMUtil;
import org.apache.log4j.Logger;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/*
* User: Dave
* Date: Jan 23, 2009
* Time: 4:48:17 PM
*/

/**
 * Html view based on HTML source stored in a module
 */
public class ModuleHtmlView extends HtmlView
{
    public static final String HTML_VIEW_EXTENSION = ".html";
    public static final String VIEW_METADATA_EXTENSION = ".view.xml";

    private Logger _log = Logger.getLogger(ModuleHtmlView.class);

    private File _htmlFile;
    private long _htmlLastModified = 0;
    private File _metadataFile;
    private long _metadataLastModified = 0;
    private String _name;
    private int _requiredPerms = 0;
    private boolean _requiresLogin = false;
    private PageConfig.Template _pageTemplate = null;

    public ModuleHtmlView(File htmlFile) throws IOException
    {
        super(null);
        _htmlFile = htmlFile;
        _htmlLastModified = _htmlFile.lastModified();
        _name = _htmlFile.getName().substring(0, _htmlFile.getName().length() - HTML_VIEW_EXTENSION.length());
        setTitle(StringUtils.capitalize(_name));

        setHtml(IOUtils.toString(new FileReader(_htmlFile)));
        loadMetaData();
    }

    protected void loadMetaData()
    {
        //look for a file with the same base name as the view file with the metadata extension
        _metadataFile = new File(_htmlFile.getParentFile(), _name + VIEW_METADATA_EXTENSION);
        if(!_metadataFile.exists() || !_metadataFile.isFile())
            return;

        _metadataLastModified = _metadataFile.lastModified();
        try
        {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(false);
            DocumentBuilder db = dbf.newDocumentBuilder();

            Document doc = db.parse(_metadataFile);
            if(null == doc || !"view".equalsIgnoreCase(doc.getDocumentElement().getNodeName()))
                return;

            Node docElem = doc.getDocumentElement();
            setTitle(DOMUtil.getAttributeValue(docElem, "title", StringUtils.capitalize(_name)));

            _requiredPerms = parseRequiredPerms(DOMUtil.getAttributeValue(docElem, "permissions"));
            FrameType frameType = parseFrameType(DOMUtil.getAttributeValue(docElem, "frame"));
            if(null != frameType)
                setFrame(frameType);

            _pageTemplate = parsePageTemplate(DOMUtil.getAttributeValue(docElem, "template"));
        }
        catch(Exception e)
        {
            _log.warn("Unable to load metadata file " + _metadataFile.getAbsolutePath(), e);
        }
    }

    protected int parseRequiredPerms(String perms)
    {
        if(null == perms || perms.length() == 0)
            return 0;

        int ret = 0;
        //perms string can be comma-delimited
        String[] permArray = perms.split(",");
        for(String permItem : permArray)
        {
            SimpleAction.Permission perm = SimpleAction.Permission.valueOf(permItem);
            if(SimpleAction.Permission.login == perm)
                _requiresLogin = true;
            else if(null != perm)
                ret |= perm.toInt();
        }

        return ret;
    }

    protected WebPartView.FrameType parseFrameType(String value)
    {
        value = StringUtils.trimToNull(value);
        return null == value ? null : WebPartView.FrameType.valueOf(value.toUpperCase());
    }

    protected PageConfig.Template parsePageTemplate(String value)
    {
        value = StringUtils.trimToNull(value);
        //page template enums are in title case!
        return null == value ? null : PageConfig.Template.valueOf(StringUtils.capitalize(value.toLowerCase()));
    }

    public boolean isStale()
    {
        return _htmlFile.lastModified() != _htmlLastModified ||
                (_metadataFile.exists() && _metadataFile.lastModified() != _metadataLastModified);
    }

    public File getHtmlFile()
    {
        return _htmlFile;
    }

    public String getName()
    {
        return _name;
    }

    public int getRequiredPerms()
    {
        return _requiredPerms;
    }

    public boolean isRequiresLogin()
    {
        return _requiresLogin;
    }

    public PageConfig.Template getPageTemplate()
    {
        return _pageTemplate;
    }
}