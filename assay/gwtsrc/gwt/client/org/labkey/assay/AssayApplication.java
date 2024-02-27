/*
 * Copyright (c) 2010-2019 LabKey Corporation
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
package gwt.client.org.labkey.assay;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.RunAsyncCallback;
import com.google.gwt.user.client.ui.RootPanel;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.PropertyUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * User: matthewb
 * Date: Mar 31, 2010
 * Time: 1:59:56 PM
 */
public class AssayApplication implements EntryPoint
{
    /**
     * As of 2.4.0, the GWT compiler NPEs if you pass an enum value in to GWT.runAsync(). Therefore, this is enum-like
     * but not an actual enum anymore.
     */
    public abstract static class GWTModule implements RunAsyncCallback
    {
        public final String className;

        private static final Set<GWTModule> MODULES = new HashSet<>();

        static
        {
            MODULES.add(new TemplateDesigner());
        }

        GWTModule(String clss)
        {
            this.className = clss;
        }

        abstract EntryPoint getEntryPoint();

        //
        // RunAsyncCallback
        //
        @Override
        public void onFailure(Throwable caught)
        {
            ErrorDialogAsyncCallback.showDialog(caught, "Failed to load code for module: " + getClass());
        }

        public static Set<GWTModule> values()
        {
            return MODULES;
        }
    }

    public static class TemplateDesigner extends GWTModule
    {
        public TemplateDesigner()
        {
            super("gwt.client.org.labkey.plate.designer.client.TemplateDesigner");
        }

        @Override
        public void onSuccess()
        {
            new gwt.client.org.labkey.plate.designer.client.TemplateDesigner().onModuleLoad();
        }

        @Override
        EntryPoint getEntryPoint()
        {
            return new gwt.client.org.labkey.plate.designer.client.TemplateDesigner();
        }
    }


    public static RootPanel getRootPanel()
    {
        String name = PropertyUtil.getServerProperty("RootPanel");
        if (null == name)
            name = "gwt.AssayApplication-Root";
        return RootPanel.get(name);
    }


    @Override
    public void onModuleLoad()
    {
        RootPanel panel = getRootPanel();
        if (null != panel)                                    
        {
            panel.getElement().setInnerHTML("");
        }
        
        final String moduleName = PropertyUtil.getServerProperty("GWTModule");

        if ("TemplateDesigner".equalsIgnoreCase(moduleName))
        {
            GWT.runAsync(new TemplateDesigner());
        }
        else
        {
            throw new IllegalArgumentException("Unknown module: " + moduleName);
        }
    }
}
