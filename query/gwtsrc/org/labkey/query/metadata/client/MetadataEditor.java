/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.query.metadata.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.Window;
import org.labkey.api.gwt.client.ui.WindowUtil;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.util.ServiceUtil;
import org.labkey.api.gwt.client.util.PropertyUtil;

import java.util.List;

public class MetadataEditor implements EntryPoint
{
    private MetadataServiceAsync _service;
    private static final String SCHEMA_NAME_PROPERTY = "schemaName";
    private static final String QUERY_NAME_PROPERTY = "queryName";
    private TablePropertiesEditor _editor;
    private String _schemaName;

    public void onModuleLoad()
    {
        _schemaName = PropertyUtil.getServerProperty(SCHEMA_NAME_PROPERTY);
        String queryName = PropertyUtil.getServerProperty(QUERY_NAME_PROPERTY);

        final RootPanel rootPanel = RootPanel.get("org.labkey.query.metadata.MetadataEditor-Root");
        rootPanel.add(new Label("Loading..."));

        getService().getMetadata(_schemaName, queryName, new AsyncCallback<GWTTableInfo>()
        {
            public void onFailure(Throwable caught)
            {
                WindowUtil.reportException("Failed to get existing metadata from server", caught);
            }

            public void onSuccess(GWTTableInfo result)
            {
                initEditor(rootPanel, result);
            }
        });
    }

    private void initEditor(RootPanel rootPanel, GWTTableInfo tableInfo)
    {
        _editor = new TablePropertiesEditor(getService());
        VerticalPanel panel = new VerticalPanel();
        _editor.addButton(new ImageButton("Save", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                List<String> errors = _editor.validate();

                if (errors.size() > 0)
                {
                    String errorString = "";
                    for (int i = 0; i < errors.size(); i++)
                    {
                        if (i > 0)
                            errorString += "\n";
                        errorString += errors.get(i);
                    }
                    Window.alert(errorString);
                }
                else
                {
                    getService().saveMetadata(_editor.getUpdates(), _schemaName, new AsyncCallback<GWTTableInfo>()
                    {
                        public void onFailure(Throwable caught)
                        {
                            WindowUtil.reportException("Failed to save metadata", caught);
                        }

                        public void onSuccess(GWTTableInfo newTableInfo)
                        {
                            _editor.init(newTableInfo);
                        }
                    });
                }
            }
        }));
        _editor.addButton(new ImageButton("Reset to Default", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                getService().resetToDefault(_schemaName, _editor.getUpdates().getName(), new AsyncCallback<GWTTableInfo>()
                {
                    public void onFailure(Throwable caught)
                    {
                        WindowUtil.reportException("Failed to reset metadata to default", caught);
                    }

                    public void onSuccess(GWTTableInfo newTableInfo)
                    {
                        _editor.init(newTableInfo);
                    }
                });
            }
        }));
        panel.add(_editor.getWidget());

        rootPanel.clear();
        rootPanel.add(panel);
        
        _editor.init(tableInfo);
    }

    private MetadataServiceAsync getService()
    {
        if (_service == null)
        {
            _service = (MetadataServiceAsync) GWT.create(MetadataService.class);
            ServiceUtil.configureEndpoint(_service, "metadataService", "query");
        }
        return _service;
    }
}
