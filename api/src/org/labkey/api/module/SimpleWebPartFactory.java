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
package org.labkey.api.module;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartConfigurationException;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.data.xml.webpart.AvailableEnum;
import org.labkey.data.xml.webpart.LocationType;
import org.labkey.data.xml.webpart.WebpartDocument;
import org.labkey.data.xml.webpart.WebpartType;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

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

    private final String _resourceName;

    private WebpartType _webPartDef = null;
    private Exception _loadException = null;

    //map from internal (ugly) location names to public names used in the XML web part definition file
    private static final Map<String,String> _locationsTranslationMap = new HashMap<>();

    static
    {
        _locationsTranslationMap.put(LOCATION_BODY, "body");
        _locationsTranslationMap.put(LOCATION_RIGHT, LOCATION_RIGHT);
        _locationsTranslationMap.put(LOCATION_MENUBAR, "menu");
    }

    public static String getFriendlyLocationName(String internalName)
    {
        return _locationsTranslationMap.get(internalName);
    }

    public static String getInternalLocationName(String friendlyName)
    {
        for (Map.Entry<String, String> entry : _locationsTranslationMap.entrySet())
        {
            if (entry.getValue().equalsIgnoreCase(friendlyName))
                return entry.getKey();
        }
        return null;
    }

    public SimpleWebPartFactory(Module module, Resource resource)
    {
        super(getNameFromFilename(resource.getName()));
        setModule(module);
        loadDefinition(resource);
        _resourceName = resource.getName();
    }

    private static String getNameFromFilename(String filename)
    {
        return filename.substring(0, filename.length() - FILE_EXTENSION.length());
    }

    public static boolean isWebPartFile(String filename)
    {
        return filename.toLowerCase().endsWith(FILE_EXTENSION);
    }

    private void loadDefinition(Resource webPartResource)
    {
        Logger log = Logger.getLogger(SimpleWebPartFactory.class);

        try (InputStream is = webPartResource.getInputStream())
        {
            _loadException = null;
            XmlOptions xmlOptions = new XmlOptions();

            Map<String,String> namespaceMap = new HashMap<>();
            namespaceMap.put("", "http://labkey.org/data/xml/webpart");
            xmlOptions.setLoadSubstituteNamespaces(namespaceMap);
            
            WebpartDocument doc = WebpartDocument.Factory.parse(is, xmlOptions);
            if(null == doc || null == doc.getWebpart())
                throw new Exception("Webpart definition file " + webPartResource.getName() + " does not contain a root 'webpart' element!");

            _webPartDef = doc.getWebpart();

            // Establish a default location, which the base class can use to do a first pass of determining if this
            // web part should be available in the list
            Set<String> locations = new LinkedHashSet<>();
            if(null != _webPartDef.getLocations())
            {
                for(LocationType declaredLoc : _webPartDef.getLocations().getLocationArray())
                {
                    String friendlyLocation = declaredLoc.getName().toString();
                    for (Map.Entry<String, String> entry : _locationsTranslationMap.entrySet())
                    {
                        if (entry.getValue().equalsIgnoreCase(friendlyLocation))
                        {
                            locations.add(entry.getKey());
                        }
                    }
                }
            }
            // Make sure we have at least one location
            if (locations.isEmpty())
            {
                // Assume we should default to the main body location
                locations.add(WebPartFactory.LOCATION_BODY);
            }
            _allowableLocations = Collections.unmodifiableSet(locations);

        }
        catch(Exception e)
        {
            _loadException = e;
            log.error(e);
        }
    }

    @Override
    public String getName()
    {
        return null != _webPartDef && null != _webPartDef.getTitle() ? _webPartDef.getTitle() : super.getName();
    }


    public String getViewName()
    {
        return null != _webPartDef && null != _webPartDef.getView() && null != _webPartDef.getView().getName() ?
                _webPartDef.getView().getName() : null;
    }


    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart) throws WebPartConfigurationException
    {
        if (null != _loadException)
            throw new WebPartConfigurationException(this, "Error thrown during load", _loadException);

        if (null == getViewName())
            throw new WebPartConfigurationException(this, "No view name specified for the module web part defined in " + _resourceName);

        WebPartView ret = ModuleHtmlView.get(getModule(), ModuleHtmlView.getStandardPath(getViewName()), webPart);
        if (null != _webPartDef && null != _webPartDef.getView())
        {
            if (null != _webPartDef.getView().getFrame())
            {
                try
                {
                    WebPartView.FrameType ft = WebPartView.FrameType.valueOf(_webPartDef.getView().getFrame().toString().toUpperCase());
                    ret.setFrame(ft);
                }
                catch (Exception x)
                {
                }
            }
// CONSIDER: should webpart title override view title?
//            if (null != _webPartDef.getTitle())
//            {
//                ret.setTitle(_webPartDef.getTitle());
//            }
        }

        if (null != _webPartDef.getCustomizeHandler() && portalCtx.hasPermission(getClass().getName(), AdminPermission.class))
        {
            NavTree customize = new NavTree("");
            customize.setScript(getWrappedOnClick(webPart, _webPartDef.getCustomizeHandler()));
            ret.setCustomize(customize);
        }


        return ret;
    }

    private String getWrappedOnClick(Portal.WebPart webPart, String originalOnClick)
    {
        if (originalOnClick == null)
            return null;

        StringBuilder onClickWrapper = new StringBuilder();
        onClickWrapper.append("var webPartRowId = ").append(PageFlowUtil.jsString(String.valueOf(webPart.getRowId()))).append("; ");
        onClickWrapper.append("var webPartProperties = ").append(Matcher.quoteReplacement(new JSONObject(webPart.getPropertyMap()).toString())).append("; ");
        onClickWrapper.append(originalOnClick);
        return onClickWrapper.toString();
    }

    @Override
    public boolean isAvailable(Container c, String location)
    {
        //if loading the definition failed, we are not available
        if (null == _webPartDef)
            return false;

        if (AvailableEnum.NEVER.equals(_webPartDef.getAvailable()))
            return false;

        //if super thinks it should be available, return true
        if(super.isAvailable(c, location))
            return true;

        //translate internal location name to public API name
        String publicLocName = getFriendlyLocationName(location);
        if(null == publicLocName)
            return false;

        //check web part definition to see when it should be available and
        //in which locations it should be available
        if(AvailableEnum.ALWAYS.equals(_webPartDef.getAvailable()) ||
                c.getActiveModules().contains(getModule()))
        {
            if(getAllowableLocations().contains(location))
                return true;
        }
        
        return false;
    }
}
