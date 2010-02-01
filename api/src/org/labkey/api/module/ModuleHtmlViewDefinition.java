/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.security.ACL;
import org.labkey.data.xml.view.PermissionType;
import org.labkey.data.xml.view.PermissionsListType;
import org.labkey.data.xml.view.ViewDocument;
import org.labkey.data.xml.view.ViewType;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

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
    private File _metadataFile;
    private int _requiredPerms = ACL.PERM_READ;  //8550: Default perms for simple module views should be read
    private boolean _requiresLogin = false;
    private ViewType _viewDef = null;

    public ModuleHtmlViewDefinition(File htmlFile)
    {
        super(htmlFile);
        _name = htmlFile.getName().substring(0, htmlFile.getName().length() - HTML_VIEW_EXTENSION.length());

        Logger log = Logger.getLogger(ModuleHtmlViewDefinition.class);
        try
        {
            _html = IOUtils.toString(new FileReader(htmlFile));
        }
        catch(IOException e)
        {
            log.error("Error trying to read HTML content from " + htmlFile.getAbsolutePath(), e);
            throw new RuntimeException(e);
        }

        _metadataFile = new File(htmlFile.getParentFile(), _name + VIEW_METADATA_EXTENSION);
        addAssociatedFile(_metadataFile);
        if(_metadataFile.exists() && _metadataFile.isFile())
        {
            try
            {
                XmlOptions xmlOptions = new XmlOptions();
                Map<String,String> namespaceMap = new HashMap<String,String>();
                namespaceMap.put("", "http://labkey.org/data/xml/view");
                xmlOptions.setLoadSubstituteNamespaces(namespaceMap);

                ViewDocument viewDoc = ViewDocument.Factory.parse(_metadataFile, xmlOptions);
                _viewDef = viewDoc.getView();
                if(null != _viewDef)
                    calculatePermissions();
            }
            catch(Exception e)
            {
                log.error("Error trying to read and parse the metadata XML content from " + _metadataFile.getAbsolutePath(), e);
                _html = "<p class='labkey-error'>The following exception occurred while attempting to load view metadata from "
                         + PageFlowUtil.filter(_metadataFile.getAbsolutePath()) + ": "
                         + e.getMessage() + "</p>";
            }
        }
    }

    protected String getTitleFromName(String name)
    {
        //convert camel case to separate words
        return ColumnInfo.labelFromName(name);
    }

    protected void calculatePermissions()
    {
        PermissionsListType permsList = _viewDef.getPermissions();
        if(null == permsList)
            return;

        PermissionType[] perms = permsList.getPermissionArray();
        if(null == perms)
            return;

        for(PermissionType permEntry : perms)
        {
            SimpleAction.Permission perm = SimpleAction.Permission.valueOf(permEntry.getName().toString());
            if(SimpleAction.Permission.login == perm)
                _requiresLogin = true;
            else if(null != perm)
                _requiredPerms |= perm.toInt();
        }
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
        return null != _viewDef && null != _viewDef.getTitle() ? _viewDef.getTitle() : getTitleFromName(_name);
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
        return null != _viewDef && null != _viewDef.getFrame() ?
                WebPartView.FrameType.valueOf(_viewDef.getFrame().toString().toUpperCase()) : null;
    }

    public PageConfig.Template getPageTemplate()
    {
        return null != _viewDef && null != _viewDef.getTemplate() ?
            PageConfig.Template.valueOf(StringUtils.capitalize(_viewDef.getTemplate().toString().toLowerCase())) : null;
    }
}