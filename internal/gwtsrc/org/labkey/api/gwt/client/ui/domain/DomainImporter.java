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
package org.labkey.api.gwt.client.ui.domain;

import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.FileUploadWithListeners;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.incubator.ProgressBar;
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

    private ImageButton importButton;
    private HTML uploadStatusLabel;
    private ProgressBarText progressBarText;
    private ProgressBar progressBar = null;

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
        this.baseColumnNames = new HashSet<String>();
        for (String colName : baseColumnNames)
        {
            this.baseColumnNames.add(colName.toLowerCase());
        }

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

        VerticalPanel uploadPanel = new VerticalPanel();
        uploadPanel.add(new HTML("Import from TSV or Excel file.<p>"));
        fileUpload = new FileUploadWithListeners();
        fileUpload.setName("uploadFormElement");
        fileUpload.addChangeListener(new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                form.submit();
            }
        });
        uploadPanel.add(fileUpload);
        panel.add(uploadPanel);

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

    @SuppressWarnings("unchecked")
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
            if (ignoredColumns.contains(propName) || baseColumnNames.contains(propName.toLowerCase()))
                continue;

            DomainImportGrid.Type selectedType = grid.getTypeForColumn(column);
            if (selectedType != null)
                prop.setRangeURI(selectedType.getXsdType());

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
                    progressBarText.setText("Importing data...");
                    importData(newDomain);
                }
                else
                {
                    handleServerFailure(errors);
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void resetDomainFields(GWTDomain domain)
    {
        service.getDomainDescriptor(domain.getDomainURI(), new AsyncCallback<GWTDomain>()
        {
            public void onFailure(Throwable caught)
            {
                handleServerFailure(caught);
            }

            public void onSuccess(GWTDomain result)
            {
                final GWTDomain newDomain = new GWTDomain(result);
                newDomain.getFields().clear();
                service.updateDomainDescriptor(result, newDomain, new AsyncCallback<List<String>>()
                {
                    public void onFailure(Throwable caught)
                    {
                        handleServerFailure(caught);
                    }
                    public void onSuccess(List<String> errors){}
                });
            }
        });
    }

    protected void importData(final GWTDomain domain)
    {
        Map<String, String> columnMap;
        if (columnMapper != null)
            columnMap = columnMapper.getColumnMap();
        else
            columnMap = new HashMap<String, String>(); // emptyMap() is not serializable
        service.importData(domain, columnMap, new AsyncCallback<ImportStatus>()
        {
            public void onFailure(Throwable caught)
            {
                resetDomainFields(domain);
                handleServerFailure(caught);
            }

            public void onSuccess(ImportStatus status)
            {
                if (!status.isComplete())
                    initProgressIndicator(status.getJobId(), domain);
                else
                    handleComplete(status, domain);
            }
        });
    }

    Timer t;

    private void initProgressIndicator(final String jobId, final GWTDomain domain)
    {
        t = new Timer() {
            boolean firstTime = true;

            public void run()
            {
                service.getStatus(jobId, new AsyncCallback<ImportStatus>()
                {
                    public void onFailure(Throwable caught)
                    {
                        resetDomainFields(domain);
                        handleServerFailure(caught);
                        cancel();
                    }

                    public void onSuccess(ImportStatus status)
                    {
                        if (status.isComplete())
                        {
                            // Update status one last time (show 100%), but not if this is the first time through.
                            if (!firstTime)
                                updateStatus(status, firstTime);

                            handleComplete(status, domain);
                            cancel();
                        }
                        else
                        {
                            updateStatus(status, firstTime);

                            if (firstTime)
                            {
                                firstTime = false;
                                t.scheduleRepeating(5000);
                            }
                        }
                    }
                });
            }
        };

        // First status check in one second; subsequent checks every five seconds.
        t.schedule(1000);
    }

    private void updateStatus(ImportStatus status, boolean firstTime)
    {
        if (status.getTotalRows() > 0)
        {
            if (firstTime)
            {
                progressBar.setMaxProgress(status.getTotalRows());
            }

            progressBar.setProgress(status.getCurrentRow());
        }
        else
        {
            // If we don't know the total number of rows we can't show a progress bar 
            progressBarText.setText("Importing data: " + status.getCurrentRow() + " rows");
        }
    }

    private void handleComplete(ImportStatus status, GWTDomain domain)
    {
        List<String> errors = status.getMessages();

        if (errors == null || errors.isEmpty())
        {
            finish();
        }
        else
        {
            resetDomainFields(domain);
            handleServerFailure(errors);
        }
    }

    private void handleServerFailure(List<String> errors)
    {
        StringBuilder sb = new StringBuilder();
        for (String error : errors)
        {
            sb.append(error).append("\n");
        }
        Window.alert(sb.toString());
    }

    private void handleServerFailure(Throwable caught)
    {
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
            uploadStatusLabel.setHTML("&nbsp;");
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
                VerticalPanel gridPanel = new VerticalPanel();
                gridPanel.add(new HTML("Showing first 5 rows:<p>"));
                gridPanel.add(grid);
                mainPanel.add(gridPanel);
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
                importButton = new ImageButton("Import", new ClickListener()
                {
                    public void onClick(Widget sender)
                    {
                        importButton.setEnabled(false);
                        progressBarText = new ProgressBarText("Creating columns...");
                        progressBar = new ProgressBar(0, 100, 0, progressBarText);
                        mainPanel.add(progressBar);
                        importData();
                    }
                });
                buttons.add(importButton);
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
    }

    private static class ProgressBarText extends ProgressBar.TextFormatter
    {
        private String _text;

        private ProgressBarText(String text)
        {
            setText(text);
        }

        private void setText(String text)
        {
            _text = text;
        }

        protected String getText(ProgressBar bar, double curProgress)
        {
            if (0.0 == curProgress)
                return _text;
            else
                return "Importing data (" + (int) (100 * bar.getPercent()) + "%)";
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

            for (int row=0; row < columnsToMap.size(); row++)
            {
                String destinationColumn = columnsToMap.get(row);
                ListBox selector = new ListBox();
                selector.setName(destinationColumn);
                int rowToSelect = row;
                for (int inferencedIndex = 0; inferencedIndex < columns.size(); inferencedIndex++)
                {
                    InferencedColumn column = columns.get(inferencedIndex);
                    String name = column.getPropertyDescriptor().getName();
                    selector.addItem(name);
                    if (areColumnNamesEquivalent(name,destinationColumn))
                        rowToSelect = inferencedIndex;
                }
                selector.setItemSelected(rowToSelect, true); // Cascade down the columns
                columnSelectors.add(selector);


                Label label = new Label(destinationColumn + ":");
                mappingGrid.setWidget(row, 1, label);
                mappingGrid.setWidget(row, 2, selector);
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

    /**
     * Try to find a reasonable match in column names, like "Visit Date" and "Date",
     * or "ParticipantID" and "participant id".
     */
    private static boolean areColumnNamesEquivalent(String col1, String col2)
    {
        col1 = col1.toLowerCase();
        col2 = col2.toLowerCase();
        col1 = col1.replaceAll(" ","");
        col2 = col2.replaceAll(" ","");
        if (col1.equals(col2))
            return true;
        if (col1.indexOf(col2) >= 0)
            return true;
        if (col2.indexOf(col1) >= 0)
            return true;
        return false;
    }

}
