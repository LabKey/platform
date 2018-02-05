/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.api.view;

import com.google.gwt.core.client.EntryPoint;
import org.labkey.api.compliance.ComplianceService;
import org.labkey.api.data.PHI;

import java.util.*;

/**
 * Wrapper around a GWT (Google Web Toolkit) module that knows how to render into the overall HTML of the page.
 * User: Mark Igra
 * Date: Jan 30, 2007
 */
public class GWTView extends JspView<GWTView.GWTViewBean>
{
    public static final String PROPERTIES_OBJECT_NAME = "LABKEY.GWTProperties";

    public static class GWTViewBean
    {
        private String _moduleName;
        private Map<String, String> _properties;

        public GWTViewBean(String moduleName, Map<String, String> properties)
        {
            _moduleName = moduleName;
            _properties = new HashMap<>(properties);
        }

        public void init(ViewContext context)
        {
            _properties.put("container", context.getContainer().getPath());
            _properties.put("controller", context.getActionURL().getController());
            _properties.put("action", context.getActionURL().getAction());
            _properties.put("queryString", context.getActionURL().getQueryString());
            _properties.put("contextPath", context.getContextPath());
            _properties.put("header1Size", ThemeFont.getThemeFont(context.getContainer()).getHeader_1Size());
            _properties.put("loadingStyle", "");

            _properties.put("maxAllowedPhi", ComplianceService.get().getMaxAllowedPhi(context.getContainer(), context.getUser()).name());
        }

        public String getModuleName()
        {
            return _moduleName;
        }

        public Map<String, String> getProperties()
        {
            return _properties;
        }

        public String getLoadingStyleName()
        {
            return _properties.get("loadingStyle");
        }

        public void setLoadingStyleName(String name)
        {
            _properties.put("loadingStyle", name);
        }
    }

    private static String convertClassToModuleName(Class<? extends EntryPoint> c)
    {
        String name = c.getName();
        int index = name.indexOf(".client.");
        if (index != -1)
        {
            name = name.substring(0, index) + name.substring(index + ".client.".length() - 1);
        }
        return name;
    }

    public GWTView(Class<? extends EntryPoint> moduleClass)
    {
        this(convertClassToModuleName(moduleClass));
    }

    public GWTView(Class<? extends EntryPoint> moduleClass, String loading)
    {
        this(convertClassToModuleName(moduleClass), Collections.singletonMap("loading",loading));
    }

    public GWTView(String moduleName)
    {
        this(moduleName, Collections.emptyMap());
    }

    public GWTView(Class<? extends EntryPoint> moduleClass, Map<String, String> properties)
    {
        this(convertClassToModuleName(moduleClass), properties);
    }

    public GWTView(String moduleName, Map<String, String> properties)
    {
        super("/org/labkey/api/view/GWTView.jsp", new GWTViewBean(moduleName, properties));
        getModelBean().init(getViewContext());
        getModulesForRootContext().add(moduleName);
    }

    public static Set<String> getModulesForRootContext()
    {
        //Not synchronized since view rendering is single threaded
        Set<String> gwtModules = (Set<String>) HttpView.getRootContext().get("gwtModules");
        if (null == gwtModules)
        {
            gwtModules = new HashSet<>();
            HttpView.getRootContext().put("gwtModules", gwtModules);
        }

        return gwtModules;
    }
}
