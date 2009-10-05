/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.http.client.URL;
import org.labkey.api.gwt.client.ui.WindowUtil;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.Saveable;
import org.labkey.api.gwt.client.util.ServiceUtil;
import org.labkey.api.gwt.client.util.PropertyUtil;

import java.util.List;

public class MetadataEditor implements EntryPoint, Saveable<GWTTableInfo>
{
    private MetadataServiceAsync _service;
    private static final String SCHEMA_NAME_PROPERTY = "schemaName";
    private static final String QUERY_NAME_PROPERTY = "queryName";
    public static final String DESIGN_QUERY_URL = "designQueryURL";
    public static final String EDIT_SOURCE_URL = "editSourceURL";
    public static final String VIEW_DATA_URL = "viewDataURL";
    private TablePropertiesEditor _editor;
    private String _schemaName;
    private ImageButton _saveButton;
    private Label _saveMessage = new Label();

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
        _editor = new TablePropertiesEditor(this, getService());
        VerticalPanel panel = new VerticalPanel();
        _saveButton = new ImageButton("Save", new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                save();
            }
        });
        _editor.addButton(_saveButton);
        _saveButton.setEnabled(false);
        _editor.getContentPanel().add(_saveMessage);

        _editor.addChangeListener(new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                _saveButton.setEnabled(_editor.isDirty());
            }
        });

        String designQueryURL = PropertyUtil.getServerProperty(DESIGN_QUERY_URL);
        if (designQueryURL != null)
        {
            _editor.addButton(createSavePromptingNavigationButton("Design Query", designQueryURL));
        }
        String editSourceURL = PropertyUtil.getServerProperty(EDIT_SOURCE_URL);
        if (editSourceURL != null)
        {
            _editor.addButton(createSavePromptingNavigationButton("Edit Source", editSourceURL));
        }
        String viewDataURL = PropertyUtil.getServerProperty(VIEW_DATA_URL);
        if (viewDataURL != null)
        {
            _editor.addButton(createSavePromptingNavigationButton("View Data", viewDataURL));
        }
        _editor.addButton(new ImageButton("Reset to Default", new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                ImageButton okButton = new ImageButton("OK", new ClickHandler()
                {
                    public void onClick(ClickEvent e)
                    {
                        _saveMessage.setText("");
                        getService().resetToDefault(_schemaName, _editor.getUpdates().getName(), new AsyncCallback<GWTTableInfo>()
                        {
                            public void onFailure(Throwable caught)
                            {
                                WindowUtil.reportException("Failed to reset metadata to default", caught);
                            }

                            public void onSuccess(GWTTableInfo newTableInfo)
                            {
                                _saveMessage.setText("Reset successful.");
                                _editor.init(newTableInfo);
                            }
                        });
                    }
                });
                
                WindowUtil.showConfirmDialog("Confirm Reset", "Are you sure you want to reset? You will lose any edits you made.", okButton);
            }
        }));
        panel.add(_editor.getWidget());

        rootPanel.clear();
        rootPanel.add(panel);
        
        _editor.init(tableInfo);
    }

    private ImageButton createSavePromptingNavigationButton(String label, final String url)
    {
        return new ImageButton(label, new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                if (_editor.isDirty())
                {
                    ImageButton saveButton = new ImageButton("Save", new ClickHandler()
                    {
                        public void onClick(ClickEvent e)
                        {
                            saveAsync(new AsyncCallback<GWTTableInfo>()
                            {
                                public void onFailure(Throwable caught)
                                {
                                    WindowUtil.reportException("Failed to saveAsync", caught);
                                }

                                public void onSuccess(GWTTableInfo result)
                                {
                                    Window.Location.replace(url);
                                }
                            });
                        }
                    });

                    ImageButton discardButton = new ImageButton("Discard", new ClickHandler()
                    {
                        public void onClick(ClickEvent e)
                        {
                            Window.Location.replace(url);
                        }
                    });

                    WindowUtil.showConfirmDialog("Save Changes?", "Do you want to save your changes?", saveButton, discardButton);
                }
                else
                {
                    Window.Location.replace(url);
                }
            }
        });
    }

    public boolean isDirty()
    {
        return _editor.isDirty();
    }

    public void save()
    {
        _saveMessage.setText("");
        save(new SaveListener<GWTTableInfo>()
        {
            public void saveSuccessful(GWTTableInfo result, String designerUrl)
            {
                _saveMessage.setText("Save successful.");
            }
        });
    }

    public void save(final SaveListener<GWTTableInfo> listener)
    {
        _saveButton.setEnabled(false);
        saveAsync(new AsyncCallback<GWTTableInfo>()
        {
            public void onFailure(Throwable caught)
            {
                _saveButton.setEnabled(true);
                WindowUtil.reportException("Failed to save", caught);
            }

            public void onSuccess(GWTTableInfo newTableInfo)
            {
                _editor.init(newTableInfo);
                if (listener != null)
                    listener.saveSuccessful(newTableInfo, PropertyUtil.getCurrentURL());
            }
        });
    }

    public static native void back() /*-{
        $wnd.history.back();
    }-*/;

    public void cancel()
    {
        back();
    }

    public void finish()
    {
        save(new SaveListener<GWTTableInfo>()
        {
            public void saveSuccessful(GWTTableInfo tableInfo, String designerUrl)
            {
                cancel();
            }
        });
    }

    /**
     * Save happens asynchronously, callback gets notified
     * @return if a save was attempted or if it failed instantly.
     */
    private boolean saveAsync(AsyncCallback<GWTTableInfo> callback)
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
            return false;
        }
        else
        {
            getService().saveMetadata(_editor.getUpdates(), _schemaName, callback);
            return true;
        }
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
