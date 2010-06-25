/*
 * Copyright (c) 2010 LabKey Corporation
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
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.PropertyUtil;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Mar 31, 2010
 * Time: 1:59:56 PM
 */
public class StudyApplication implements EntryPoint
{
    public enum GWTModule implements RunAsyncCallback
    {
        AssayDesigner("gwt.client.org.labkey.assay.designer.client.AssayDesigner")
        {
            public void onSuccess()
            {
                new gwt.client.org.labkey.assay.designer.client.AssayDesigner().onModuleLoad();
            }
            EntryPoint getEntryPoint()
            {
                return new gwt.client.org.labkey.assay.designer.client.AssayDesigner();
            }
        },
        ListChooser("gwt.client.org.labkey.assay.upload.client.ListChooser")
        {
            public void onSuccess()
            {
                new gwt.client.org.labkey.assay.upload.client.ListChooser().onModuleLoad();
            }
            EntryPoint getEntryPoint()
            {
                return new gwt.client.org.labkey.assay.upload.client.ListChooser();
            }
        },
        TemplateDesigner("gwt.client.org.labkey.plate.designer.client.TemplateDesigner")
        {
            public void onSuccess()
            {
                new gwt.client.org.labkey.plate.designer.client.TemplateDesigner().onModuleLoad();
            }
            EntryPoint getEntryPoint()
            {
                return new gwt.client.org.labkey.plate.designer.client.TemplateDesigner();
            }
        },
        StudyChartDesigner("gwt.client.org.labkey.study.chart.client.StudyChartDesigner")
        {
            public void onSuccess()
            {
                new gwt.client.org.labkey.study.chart.client.StudyChartDesigner().onModuleLoad();
            }
            EntryPoint getEntryPoint()
            {
                return new gwt.client.org.labkey.study.chart.client.StudyChartDesigner();
            }
        },
        DatasetImporter("gwt.client.org.labkey.study.dataset.client.DatasetImporter")
        {
            public void onSuccess()
            {
                new gwt.client.org.labkey.study.dataset.client.DatasetImporter().onModuleLoad();
            }
            EntryPoint getEntryPoint()
            {
                return new gwt.client.org.labkey.study.dataset.client.DatasetImporter();
            }
        },
        DatasetDesigner("gwt.client.org.labkey.study.dataset.client.Designer")
        {
            public void onSuccess()
            {
                new gwt.client.org.labkey.study.dataset.client.Designer().onModuleLoad();
            }
            EntryPoint getEntryPoint()
            {
                return new gwt.client.org.labkey.study.dataset.client.Designer();
            }
        },
        StudyDesigner("gwt.client.org.labkey.study.designer.client.Designer")
        {
            public void onSuccess()
            {
                new gwt.client.org.labkey.study.designer.client.Designer().onModuleLoad();
            }
            EntryPoint getEntryPoint()
            {
                return new gwt.client.org.labkey.study.designer.client.Designer();
            }
        };


        public final String className;

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
            ErrorDialogAsyncCallback.showDialog(caught, "Failed to load code for module: " + this.name());
        }


// doesn't work.  creates trival split points
//        public void onSuccess()
//        {
//            RootPanel panel = getRootPanel();
//            if (null != panel)
//                panel.clear();  // clear Loading...
//            getEntryPoint().onModuleLoad();
//        }
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
        GWTModule module = GWTModule.valueOf(moduleName);

        // this only creates one split point, need multiple calls to GWT.runAsync() to create multiple split points
        // GWT.runAsync(module);

        switch (module)
        {
            case AssayDesigner:
                GWT.runAsync(GWTModule.AssayDesigner);
                break;
            case ListChooser:
                GWT.runAsync(GWTModule.ListChooser);
                break;
            case TemplateDesigner:
                GWT.runAsync(GWTModule.TemplateDesigner);
                break;
            case StudyChartDesigner:
                GWT.runAsync(GWTModule.StudyChartDesigner);
                break;
            case DatasetImporter:
                GWT.runAsync(GWTModule.DatasetImporter);
                break;
            case DatasetDesigner:
                GWT.runAsync(GWTModule.DatasetDesigner);
                break;
            case StudyDesigner:
                GWT.runAsync(GWTModule.StudyDesigner);
                break;
        }
    }
}
