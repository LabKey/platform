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
package org.labkey.api.gwt.client.ui.domain;

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.util.PropertyUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * GWT Class for defining a domain from a file,
 * and then importing that data
 *
 * User: jgarms
 * Date: Nov 3, 2008
 */
public class DomainImporter
{
    private DomainImporterServiceAsync service;

    /**
     * Contains the list of columns from the data file that must be mapped
     * to columns in the new domain. E.g. "ParticipantID" or "SequenceNum".
     */
    private List<String> columnsToMap;

    private VerticalPanel mainPanel;

    FileUpload fileUpload;

    private Label statusLabel;

    List<InferencedColumn> columns;

    private DomainImportGrid grid;
    private ColumnMapper columnMapper;


    private String cancelURL;
    private String successURL;
    private GWTDomain domain;


    public DomainImporter(DomainImporterServiceAsync service, List<String> columnsToMap)
    {
        this.service = service;
        this.columnsToMap = columnsToMap;

        successURL = PropertyUtil.getServerProperty("successURL");
        cancelURL = PropertyUtil.getServerProperty("cancelURL");

        VerticalPanel panel = new VerticalPanel();

        final FormPanel form = new FormPanel();

        String url = PropertyUtil.getRelativeURL("uploadFileForInferencing", "property");
        form.setAction(url);

        form.setEncoding(FormPanel.ENCODING_MULTIPART);
        form.setMethod(FormPanel.METHOD_POST);

        form.addFormHandler(new UploadFormHandler());
        
        form.setWidget(panel);

        fileUpload = new FileUpload();
        fileUpload.setName("uploadFormElement");
        panel.add(fileUpload);

        statusLabel = new HTML("&nbsp;");

        final ImageButton submitButton = new ImageButton("Update", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                form.submit();
            }
        });

        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.add(submitButton);
        buttonPanel.add(statusLabel);

        panel.add(buttonPanel);

        mainPanel = new VerticalPanel();
        mainPanel.add(form);
    }

    public Panel getMainPanel()
    {
        return mainPanel;
    }

    private void finish()
    {
        Window.alert("Not yet implemented");
        navigate(successURL);
    }

    private void cancel()
    {
        if (null == cancelURL || cancelURL.length() == 0)
            back();
        else
            navigate(cancelURL);
    }

    public static native void navigate(String url) /*-{
      $wnd.location.href = url;
    }-*/;


    public static native void back() /*-{
        $wnd.history.back();
    }-*/;

    private class UploadFormHandler implements FormHandler
    {
        public void onSubmit(FormSubmitEvent event)
        {

            if(fileUpload.getFilename().length() == 0)
            {
                Window.alert("Please select a file to upload");
                event.setCancelled(true);
                return;
            }

            statusLabel.setText("Uploading...");
        }

        public void onSubmitComplete(FormSubmitCompleteEvent event)
        {
            statusLabel.setText("Processing...");

            service.inferenceColumns(new AsyncCallback()
            {
                public void onFailure(Throwable caught)
                {
                    statusLabel.setText("");
                    Window.alert("Failure:\n" + caught.getMessage());
                }

                public void onSuccess(Object result)
                {
                    //noinspection unchecked
                    columns = (List)result;
                    statusLabel.setText("");
                    boolean needGridAndButtons = false;
                    if (grid == null)
                    {
                        needGridAndButtons = true;
                        grid = new DomainImportGrid();
                        mainPanel.add(grid);
                    }
                    grid.setColumns(columns);
                    if (!needGridAndButtons)
                    {
                        // We've already been through here once, remove our old mapper
                        mainPanel.remove(columnMapper);
                    }
                    columnMapper = new ColumnMapper();
                    mainPanel.insert(columnMapper, 2);

                    if (needGridAndButtons)
                    {
                        HorizontalPanel buttons = new HorizontalPanel();
                        buttons.add(new ImageButton("Import", new ClickListener()
                        {
                            public void onClick(Widget sender)
                            {
                                finish();
                            }
                        }));
                        buttons.add(new ImageButton("Cancel", new ClickListener()
                        {
                            public void onClick(Widget sender)
                            {
                                cancel();
                            }
                        }));

                        mainPanel.add(buttons);
                    }
                }
            });
        }
    }

    private class ColumnMapper extends VerticalPanel
    {
        List<ListBox> columnSelectors;

        public ColumnMapper()
        {
            super();
            columnSelectors = new ArrayList<ListBox>();

            add(new HTML("Column Mapping:"));

            Grid mappingGrid = new Grid(columnsToMap.size(), 3);
            add(mappingGrid);

            int row=0;
            for (String destinationColumn : columnsToMap)
            {
                ListBox selector = new ListBox();
                for (InferencedColumn column : columns)
                {
                    selector.addItem(column.getPropertyDescriptor().getName());
                }
                selector.setItemSelected(row, true); // Cascade down the columns
                columnSelectors.add(selector);


                Label label = new Label(destinationColumn + ":");
                mappingGrid.setWidget(row, 1, label);
                mappingGrid.setWidget(row, 2, selector);

                row++;
            }
        }
    }

}
