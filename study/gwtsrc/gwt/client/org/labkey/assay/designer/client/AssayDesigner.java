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

package gwt.client.org.labkey.assay.designer.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.user.client.ui.RootPanel;
import gwt.client.org.labkey.study.StudyApplication;
import org.labkey.api.gwt.client.assay.AssayDesignerMainPanel;

/**
 * User: brittp
 * Date: Jun 20, 2007
 * Time: 2:23:06 PM
 */
public class AssayDesigner implements EntryPoint
{
    public void onModuleLoad()
    {
        RootPanel panel = StudyApplication.getRootPanel();
        if (null == panel)
            panel = RootPanel.get("gwt.AssayDesigner-Root");
        if (panel != null)
        {
            AssayDesignerMainPanel view = new AssayDesignerMainPanel(panel);
            view.showAsync();
        }
    }
}
