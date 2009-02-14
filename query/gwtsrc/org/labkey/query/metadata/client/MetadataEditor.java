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
    private TablePropertiesEditor _editor;
    private String _schemaName;
    private DialogBox _confirmDialog;
    private ImageButton _saveButton;

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
        _saveButton = new ImageButton("Save", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                save();
            }
        });
        _editor.addButton(_saveButton);
        _saveButton.setEnabled(false);
        _editor.addChangeListener(new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                _saveButton.setEnabled(_editor.isDirty());
            }
        });

        _editor.addButton(new ImageButton("Reset to Default", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                ImageButton okButton = new ImageButton("OK", new ClickListener()
                {
                    public void onClick(Widget sender)
                    {
                        _confirmDialog.hide();
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
                });
                
                showConfirmDialog("Confirm Reset", "Are you sure you want to reset? You will lose any edits you made.", okButton);
            }
        }));
        final String xmlActionURL = PropertyUtil.getServerProperty("xmlActionURL");
        if (xmlActionURL != null)
        {
            _editor.addButton(new ImageButton("Edit as XML", new ClickListener()
            {
                public void onClick(Widget sender)
                {
                    if (_editor.isDirty())
                    {
                        ImageButton saveButton = new ImageButton("Save", new ClickListener()
                        {
                            public void onClick(Widget sender)
                            {
                                saveAsync(new AsyncCallback<GWTTableInfo>()
                                {
                                    public void onFailure(Throwable caught)
                                    {
                                        WindowUtil.reportException("Failed to saveAsync", caught);
                                    }

                                    public void onSuccess(GWTTableInfo result)
                                    {
                                        Window.Location.replace(xmlActionURL);
                                    }
                                });
                            }
                        });

                        ImageButton discardButton = new ImageButton("Discard", new ClickListener()
                        {
                            public void onClick(Widget sender)
                            {
                                Window.Location.replace(xmlActionURL);
                            }
                        });

                        showConfirmDialog("Save Changes?", "Do you want to save your changes?", saveButton, discardButton);
                    }
                    else
                    {
                        Window.Location.replace(xmlActionURL);
                    }
                }
            }));
        }
        panel.add(_editor.getWidget());

        rootPanel.clear();
        rootPanel.add(panel);
        
        _editor.init(tableInfo);
    }

    public void hideConfirmDialog()
    {
        if (_confirmDialog != null)
        {
            _confirmDialog.hide();
            _confirmDialog = null;
        }
    }

    /**
     * Shows a modal popup to ask for confirmation. Automatically adds a cancel button that just dismisses the dialog.
     */
    public void showConfirmDialog(String title, String message, ImageButton... buttons)
    {
        _confirmDialog = new DialogBox(false, true);
        _confirmDialog.setText(title);
        VerticalPanel panel = new VerticalPanel();
        panel.add(new Label(message));
        HorizontalPanel buttonPanel = new HorizontalPanel();

        for (ImageButton button : buttons)
        {
            buttonPanel.add(button);
        }
        ImageButton cancelButton = new ImageButton("Cancel", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                _confirmDialog.hide();
            }
        });
        buttonPanel.add(cancelButton);
        buttonPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        panel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);

        panel.add(buttonPanel);
        _confirmDialog.add(panel);
        _confirmDialog.show();
        WindowUtil.centerDialog(_confirmDialog);
    }

    public boolean isDirty()
    {
        return _editor.isDirty();
    }

    public void save()
    {
        save(null);
    }

    public void save(final SaveListener<GWTTableInfo> listener)
    {
        _saveButton.setEnabled(false);
        saveAsync(new AsyncCallback<GWTTableInfo>()
        {
            public void onFailure(Throwable caught)
            {
                WindowUtil.reportException("Failed to save", caught);
            }

            public void onSuccess(GWTTableInfo newTableInfo)
            {
                _editor.init(newTableInfo);
                if (listener != null)
                    listener.saveSuccessful(newTableInfo);
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
            public void saveSuccessful(GWTTableInfo tableInfo)
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
