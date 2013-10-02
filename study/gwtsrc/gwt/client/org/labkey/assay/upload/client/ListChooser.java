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

package gwt.client.org.labkey.assay.upload.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import gwt.client.org.labkey.study.StudyApplication;
import org.labkey.api.gwt.client.assay.AssayService;
import org.labkey.api.gwt.client.assay.AssayServiceAsync;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.*;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;

/**
 * User: jeckels
 * Date: Sep 24, 2007
 */
public class ListChooser implements EntryPoint, LookupListener<GWTPropertyDescriptor>
{
    private LookupEditor _lookupEditor;
    private Label _label;
    private AssayServiceAsync _service;

    public void onModuleLoad()
    {
        RootPanel rootPanel = StudyApplication.getRootPanel();
        if (null == rootPanel)
            rootPanel = RootPanel.get("gwt.ListChooser-Root");
        
        HorizontalPanel panel = new HorizontalPanel();
        rootPanel.add(panel);
        panel.setSpacing(5);
        panel.setVerticalAlignment(HorizontalPanel.ALIGN_MIDDLE);
        panel.add(new ImageButton("Choose list...", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                GWTPropertyDescriptor pd = createPropertyDescriptorFromForm();
                _lookupEditor.init(pd);

                _lookupEditor.show();

                int x = (Window.getClientWidth() - _lookupEditor.getOffsetWidth()) / 2;
                int y = (Window.getClientHeight() - _lookupEditor.getOffsetHeight()) / 2;
                _lookupEditor.setPopupPosition(x, y);
            }
        }));
        _label = new Label();
        panel.add(_label);
        _lookupEditor = new LookupEditor(getService(), this, true);
        _lookupEditor.setTitle(PropertyUtil.getServerProperty("dialogTitle"));

        lookupUpdated(createPropertyDescriptorFromForm());
    }

    private GWTPropertyDescriptor createPropertyDescriptorFromForm()
    {
        GWTPropertyDescriptor pd = new GWTPropertyDescriptor();
        pd.setLookupContainer(FormUtil.getValueInForm(getContainerElement()));
        pd.setLookupSchema(FormUtil.getValueInForm(getSchemaElement()));
        pd.setLookupQuery(FormUtil.getValueInForm(getQueryElement()));
        return pd;
    }

    private Element getContainerElement()
    {
        return DOM.getElementById("ThawListList-Container");
    }

    public void lookupUpdated(GWTPropertyDescriptor pd)
    {
        FormUtil.setValueInForm(pd.getLookupContainer(), getContainerElement());
        FormUtil.setValueInForm(pd.getLookupSchema(), getSchemaElement());
        FormUtil.setValueInForm(pd.getLookupQuery(), getQueryElement());

        String description;
        if (pd.getLookupSchema() != null && pd.getLookupQuery() != null)
        {
            description = pd.getLookupSchema() + "." + pd.getLookupQuery();
            if (pd.getLookupContainer() != null)
            {
                description += " in " + pd.getLookupContainer();
            }
        }
        else
        {
            description = "No list is currently selected.";
        }
        _label.setText(description);
    }

    private Element getQueryElement()
    {
        return DOM.getElementById("ThawListList-QueryName");
    }

    private Element getSchemaElement()
    {
        return DOM.getElementById("ThawListList-SchemaName");
    }

    private AssayServiceAsync getService()
    {
        if (_service == null)
        {
            _service = GWT.create(AssayService.class);
            ServiceUtil.configureEndpoint(_service, "service", PropertyUtil.getServerProperty(StudyApplication.ListChooser.CONTROLLER_PROPERTY_NAME));
        }
        return _service;
    }
}
