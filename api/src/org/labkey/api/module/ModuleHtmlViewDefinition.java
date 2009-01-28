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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.util.DOMUtil;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/*
* User: Dave
* Date: Jan 26, 2009
* Time: 1:43:47 PM
*/

/**
 * Definition of a file-based html view in a module.
 * This is separate from ModuleHtmlView so that it can be cached
 */
public class ModuleHtmlViewDefinition extends ModuleFileResource
{
    public static final String HTML_VIEW_EXTENSION = ".html";
    public static final String VIEW_METADATA_EXTENSION = ".view.xml";

    private String _name;
    private String _html;
    private String _title;
    private File _metadataFile;
    private int _requiredPerms = 0;
    private boolean _requiresLogin = false;
    private WebPartView.FrameType _frameType = null;
    private PageConfig.Template _pageTemplate = null;

    private Logger _log = Logger.getLogger(ModuleHtmlViewDefinition.class);

    public ModuleHtmlViewDefinition(File htmlFile)
    {
        super(htmlFile);
        _name = htmlFile.getName().substring(0, htmlFile.getName().length() - HTML_VIEW_EXTENSION.length());
        _title = StringUtils.capitalize(_name);

        try
        {
            _html = IOUtils.toString(new FileReader(htmlFile));
        }
        catch(IOException e)
        {
            _log.error("Error trying to read HTML content from " + htmlFile.getAbsolutePath(), e);
            throw new RuntimeException(e);
        }

        _metadataFile = new File(htmlFile.getParentFile(), _name + VIEW_METADATA_EXTENSION);
        if(_metadataFile.exists() && _metadataFile.isFile())
        {
            addAssociatedFile(_metadataFile);
            try
            {
                loadMetaData(parseFile(_metadataFile));
            }
            catch(Exception e)
            {
                _log.error("Error trying to read and parse the metadata XML content from " + _metadataFile.getAbsolutePath(), e);
                throw new RuntimeException(e);
            }
        }
    }

    protected void loadMetaData(Document doc)
    {
        if(null == doc || !"view".equalsIgnoreCase(doc.getDocumentElement().getNodeName()))
            return;

        Node docElem = doc.getDocumentElement();
        _title = DOMUtil.getAttributeValue(docElem, "title", StringUtils.capitalize(_name));

        _requiredPerms = parseRequiredPerms(DOMUtil.getFirstChildNodeWithName(docElem, "permissions"));
        _frameType = parseFrameType(DOMUtil.getAttributeValue(docElem, "frame"));
        _pageTemplate = parsePageTemplate(DOMUtil.getAttributeValue(docElem, "template"));
    }

    protected int parseRequiredPerms(Node permsElem)
    {
        if(null == permsElem)
            return 0;

        int ret = 0;
        for(Node childElem : DOMUtil.getChildNodesWithName(permsElem, "permission"))
        {
            String nameAttr = DOMUtil.getAttributeValue(childElem, "name");
            if(null == nameAttr)
                continue;

            SimpleAction.Permission perm = SimpleAction.Permission.valueOf(nameAttr);
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

    public String getName()
    {
        return _name;
    }

    public File getMetadataFile()
    {
        return _metadataFile;
    }

    public String getHtml()
    {
        return _html;
    }

    public String getTitle()
    {
        return _title;
    }

    public int getRequiredPerms()
    {
        return _requiredPerms;
    }

    public boolean isRequiresLogin()
    {
        return _requiresLogin;
    }

    public WebPartView.FrameType getFrameType()
    {
        return _frameType;
    }

    public PageConfig.Template getPageTemplate()
    {
        return _pageTemplate;
    }
}