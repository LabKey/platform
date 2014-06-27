/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
package gwt.client.org.labkey.study;

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
public class StudyApplication implements EntryPoint
{
    /**
     * As of 2.4.0, the GWT compiler NPEs if you pass an enum value in to GWT.runAsync(). Therefore, this is enum-like
     * but not an actual enum anymore.
     */
    public abstract static class GWTModule implements RunAsyncCallback
    {
        public final String className;

        private static final Set<GWTModule> MODULES = new HashSet<GWTModule>();
        static
        {
            MODULES.add(new AssayDesigner());
            MODULES.add(new AssayImporter());
            MODULES.add(new TemplateDesigner());
            MODULES.add(new StudyChartDesigner());
            MODULES.add(new DatasetImporter());
            MODULES.add(new DatasetDesigner());
            MODULES.add(new SpecimenDesigner());
            MODULES.add(new StudyDesigner());
        }

        GWTModule(String clss)
        {
            this.className = clss;
        }

        abstract EntryPoint getEntryPoint();

        //
        // RunAsyncCallback
        //
        public void onFailure(Throwable caught)
        {
            ErrorDialogAsyncCallback.showDialog(caught, "Failed to load code for module: " + getClass());
        }

        public static Set<GWTModule> values()
        {
            return MODULES;
        }
    }

    public static class AssayDesigner extends GWTModule
    {
        public AssayDesigner()
        {
            super("gwt.client.org.labkey.assay.designer.client.AssayDesigner");
        }

        public void onSuccess()
        {
            new gwt.client.org.labkey.assay.designer.client.AssayDesigner().onModuleLoad();
        }
        EntryPoint getEntryPoint()
        {
            return new gwt.client.org.labkey.assay.designer.client.AssayDesigner();
        }
    }
    
    public static class AssayImporter extends GWTModule
    {
        public AssayImporter()
        {
            super(("gwt.client.org.labkey.assay.designer.client.AssayImporter"));
        }

        public void onSuccess()
        {
            new gwt.client.org.labkey.assay.designer.client.AssayImporter().onModuleLoad();
        }
        EntryPoint getEntryPoint()
        {
            return new gwt.client.org.labkey.assay.designer.client.AssayImporter();
        }
    }

    public static class TemplateDesigner extends GWTModule
    {
        public TemplateDesigner()
        {
            super("gwt.client.org.labkey.plate.designer.client.TemplateDesigner");
        }

        public void onSuccess()
        {
            new gwt.client.org.labkey.plate.designer.client.TemplateDesigner().onModuleLoad();
        }
        EntryPoint getEntryPoint()
        {
            return new gwt.client.org.labkey.plate.designer.client.TemplateDesigner();
        }
    }

    public static class StudyChartDesigner extends GWTModule
    {
        public StudyChartDesigner()
        {
            super("gwt.client.org.labkey.study.chart.client.StudyChartDesigner");
        }

        public void onSuccess()
        {
            new gwt.client.org.labkey.study.chart.client.StudyChartDesigner().onModuleLoad();
        }
        EntryPoint getEntryPoint()
        {
            return new gwt.client.org.labkey.study.chart.client.StudyChartDesigner();
        }
    }

    public static class DatasetImporter extends GWTModule
    {
        public DatasetImporter()
        {
            super("gwt.client.org.labkey.study.dataset.client.DatasetImporter");
        }

        public void onSuccess()
        {
            new gwt.client.org.labkey.study.dataset.client.DatasetImporter().onModuleLoad();
        }
        EntryPoint getEntryPoint()
        {
            return new gwt.client.org.labkey.study.dataset.client.DatasetImporter();
        }
    }

    public static class DatasetDesigner extends GWTModule
    {
        public DatasetDesigner()
        {
            super("gwt.client.org.labkey.study.dataset.client.Designer");
        }

        public void onSuccess()
        {
            new gwt.client.org.labkey.study.dataset.client.Designer().onModuleLoad();
        }
        EntryPoint getEntryPoint()
        {
            return new gwt.client.org.labkey.study.dataset.client.Designer();
        }
    }


    public static class SpecimenDesigner extends GWTModule
    {
        public SpecimenDesigner()
        {
            super("gwt.client.org.labkey.specimen.client.SpecimenDesigner");
        }

        public void onSuccess()
        {
            new gwt.client.org.labkey.specimen.client.SpecimenDesigner().onModuleLoad();
        }

        EntryPoint getEntryPoint()
        {
            return new gwt.client.org.labkey.specimen.client.SpecimenDesigner();
        }
    }


    public static class StudyDesigner extends GWTModule
    {
        public StudyDesigner()
        {
            super("gwt.client.org.labkey.study.designer.client.Designer");
        }

        public void onSuccess()
        {
            new gwt.client.org.labkey.study.designer.client.Designer().onModuleLoad();
        }
        
        EntryPoint getEntryPoint()
        {
            return new gwt.client.org.labkey.study.designer.client.Designer();
        }
    }
    
    public static RootPanel getRootPanel()
    {
        String name = PropertyUtil.getServerProperty("RootPanel");
        if (null == name)
            name = "gwt.StudyApplication-Root";
        return RootPanel.get(name);
    }



    public void onModuleLoad()
    {
        RootPanel panel = getRootPanel();
        if (null != panel)                                    
        {
            panel.getElement().setInnerHTML("");
        }
        
        final String moduleName = PropertyUtil.getServerProperty("GWTModule");
        
        if ("AssayDesigner".equalsIgnoreCase(moduleName))
        {
            GWT.runAsync(new AssayDesigner());
        }
        else if ("AssayImporter".equalsIgnoreCase(moduleName))
        {
            GWT.runAsync(new AssayImporter());
        }
        else if ("TemplateDesigner".equalsIgnoreCase(moduleName))
        {
            GWT.runAsync(new TemplateDesigner());
        }
        else if ("StudyChartDesigner".equalsIgnoreCase(moduleName))
        {
            GWT.runAsync(new StudyChartDesigner());
        }
        else if ("DatasetImporter".equalsIgnoreCase(moduleName))
        {
            GWT.runAsync(new DatasetImporter());
        }
        else if ("DatasetDesigner".equalsIgnoreCase(moduleName))
        {
            GWT.runAsync(new DatasetDesigner());
        }
        else if ("StudyDesigner".equalsIgnoreCase(moduleName))
        {
            GWT.runAsync(new StudyDesigner());
        }
        else if ("SpecimenDesigner".equalsIgnoreCase(moduleName))
        {
            GWT.runAsync(new SpecimenDesigner());
        }
        else
        {
            throw new IllegalArgumentException("Unknown module: " + moduleName);
        }
    }
}
