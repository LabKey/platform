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
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.FileUploadWithListeners;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.util.PropertyUtil;

import java.util.*;

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
     *
     * The mapped columns will not be created in the domain. Subclasses will
     * handle that if anything needs to be done. The expectation is that these
     * mapped columns probably come from columns in the hard table,
     * and thus do not need ontology manager columns created.
     */
    private List<String> columnsToMap;

    private final boolean needToMapColumns;

    /**
     * Contains a set of columns that already exist in an underlying hard table.
     * E.g. "modified", etc.
     */
    private Set<String> baseColumnNames;

    private VerticalPanel mainPanel;

    FileUploadWithListeners fileUpload;

    private HTML uploadStatusLabel;
    private Label importStatusLabel;

    List<InferencedColumn> columns;

    private DomainImportGrid grid;

    private ColumnMapper columnMapper;


    private String cancelURL;
    private String successURL;
    private String typeURI;

    public DomainImporter(DomainImporterServiceAsync service, List<String> columnsToMap, Set<String> baseColumnNames)
    {
        this.service = service;
        this.columnsToMap = columnsToMap;
        this.needToMapColumns = columnsToMap.size() > 0;
        this.baseColumnNames = baseColumnNames;

        successURL = PropertyUtil.getServerProperty("successURL");
        cancelURL = PropertyUtil.getServerProperty("cancelURL");
        typeURI = PropertyUtil.getServerProperty("typeURI");

        VerticalPanel panel = new VerticalPanel();

        final FormPanel form = new FormPanel();

        String url = PropertyUtil.getRelativeURL("uploadFileForInferencing", "property");
        form.setAction(url);

        form.setEncoding(FormPanel.ENCODING_MULTIPART);
        form.setMethod(FormPanel.METHOD_POST);

        form.addFormHandler(new UploadFormHandler());
        
        form.setWidget(panel);

        fileUpload = new FileUploadWithListeners();
        fileUpload.setName("uploadFormElement");
        fileUpload.addChangeListener(new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                form.submit();
            }
        });
        panel.add(fileUpload);

        uploadStatusLabel = new HTML("&nbsp;");

        panel.add(uploadStatusLabel);

        mainPanel = new VerticalPanel();
        mainPanel.add(form);
    }

    public Panel getMainPanel()
    {
        return mainPanel;
    }

    public void finish()
    {
        navigate(successURL);
    }

    private void importData()
    {
        importStatusLabel.setText("Creating columns...");
        service.getDomainDescriptor(typeURI, new AsyncCallback<GWTDomain>()
        {
            public void onFailure(Throwable caught)
            {
                handleServerFailure(caught);
            }

            public void onSuccess(GWTDomain result)
            {
                createColumnsOnServer(result);
            }
        });
    }

    protected void createColumnsOnServer(GWTDomain domain)
    {
        final GWTDomain newDomain = new GWTDomain(domain);
        Set<String> ignoredColumns;
        if (columnMapper != null)
            ignoredColumns = columnMapper.getMappedColumnNames();
        else
            ignoredColumns = new HashSet<String>(); // emptySet is not serializable
        List<GWTPropertyDescriptor> newProps = newDomain.getFields();
        for (InferencedColumn column : columns)
        {
            // Don't create properties for columns we're mapping, or that are already in the base table
            GWTPropertyDescriptor prop = column.getPropertyDescriptor();
            String propName = prop.getName();
            if (ignoredColumns.contains(propName) || baseColumnNames.contains(propName))
                continue;

            newProps.add(prop);
        }

        service.updateDomainDescriptor(domain, newDomain, new AsyncCallback<List<String>>()
        {
            public void onFailure(Throwable caught)
            {
                handleServerFailure(caught);
            }

            public void onSuccess(List<String> errors)
            {
                if (errors == null || errors.isEmpty())
                {
                    importStatusLabel.setText("Importing data...");
                    importData(newDomain);
                }
                else
                {
                    handleServerFailure(errors);
                }
            }
        });
    }

    protected void importData(GWTDomain domain)
    {
        Map<String,String> columnMap;
        if (columnMapper != null)
            columnMap = columnMapper.getColumnMap();
        else
            columnMap = new HashMap<String,String>(); // emptyMap() is not serializable
        service.importData(domain, columnMap, new AsyncCallback<List<String>>()
        {
            public void onFailure(Throwable caught)
            {
                handleServerFailure(caught);
            }

            public void onSuccess(List<String> errors)
            {
                if (errors == null || errors.isEmpty())
                {
                    finish();
                }
                else
                {
                    handleServerFailure(errors);
                }
            }
        });
    }

    private void handleServerFailure(List<String> errors)
    {
        StringBuilder sb = new StringBuilder();
        for (String error : errors)
        {
            sb.append(error).append("\n");
        }
        importStatusLabel.setText("");
        Window.alert(sb.toString());
    }

    private void handleServerFailure(Throwable caught)
    {
        importStatusLabel.setText("");
        Window.alert(caught.getMessage());
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

    private class UploadFormHandler implements FormHandler, AsyncCallback<List<InferencedColumn>>
    {
        public void onSubmit(FormSubmitEvent event)
        {

            if(fileUpload.getFilename().length() == 0)
            {
                Window.alert("Please select a file to upload");
                event.setCancelled(true);
                return;
            }

            uploadStatusLabel.setText("Uploading...");
        }

        public void onSubmitComplete(FormSubmitCompleteEvent event)
        {
            uploadStatusLabel.setText("Processing...");

            service.inferenceColumns(this);
        }

        public void onFailure(Throwable caught)
        {
            uploadStatusLabel.setHTML("&nsbp;");
            Window.alert("Failure:\n" + caught.getMessage());
        }

        public void onSuccess(List<InferencedColumn> result)
        {
            uploadStatusLabel.setHTML("&nbsp;");
            columns = result;
            boolean needGridAndButtons = false;
            if (grid == null)
            {
                needGridAndButtons = true;
                grid = new DomainImportGrid();
                mainPanel.add(grid);
            }
            grid.setColumns(columns);
            if (!needGridAndButtons && needToMapColumns)
            {
                // We've already been through here once, remove our old mapper
                mainPanel.remove(columnMapper);
            }
            if (needToMapColumns)
            {
                columnMapper = new ColumnMapper();
                mainPanel.insert(columnMapper, 2);
            }

            if (needGridAndButtons)
            {
                HorizontalPanel buttons = new HorizontalPanel();
                buttons.add(new ImageButton("Import", new ClickListener()
                {
                    public void onClick(Widget sender)
                    {
                        importData();
                    }
                }));
                buttons.add(new ImageButton("Cancel", new ClickListener()
                {
                    public void onClick(Widget sender)
                    {
                        cancel();
                    }
                }));
                importStatusLabel = new HTML("&nbsp;");
                buttons.add(importStatusLabel);

                mainPanel.add(buttons);
            }
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
                selector.setName(destinationColumn);
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

        public Set<String> getMappedColumnNames()
        {
            Set<String> columnNames = new HashSet<String>();
            for (ListBox listBox : columnSelectors)
            {
                columnNames.add(listBox.getItemText(listBox.getSelectedIndex()));
            }
            return columnNames;
        }

        /**
         * Map of column in the file -> column in the database
         */
        public Map<String,String> getColumnMap()
        {
            Map<String,String> result = new HashMap<String,String>();
            for(int i=0; i<columnsToMap.size(); i++)
            {
                String dataColumn = columnsToMap.get(i);
                ListBox selector = columnSelectors.get(i);
                String fileColumn = selector.getItemText(selector.getSelectedIndex());

                result.put(fileColumn, dataColumn);
            }
            return result;
        }
    }

}
