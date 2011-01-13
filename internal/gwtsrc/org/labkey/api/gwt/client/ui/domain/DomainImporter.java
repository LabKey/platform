/*
 * Copyright (c) 2008-2011 LabKey Corporation
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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.ChangeListener;
import com.google.gwt.user.client.ui.FormHandler;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormSubmitCompleteEvent;
import com.google.gwt.user.client.ui.FormSubmitEvent;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.FileUploadWithListeners;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.incubator.ProgressBar;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private GWTDomain _domain;

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
    private FileUploadWithListeners fileUpload;
    private ImageButton importButton;
    private HTML uploadStatusLabel;
    private ProgressBarText progressBarText;
    private ProgressBar progressBar = null;
    private List<InferencedColumn> columns;
    private DomainImportGrid<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor> grid;
    private ColumnMapper columnMapper;

    private boolean cancelRequested = false;
    private boolean _hideFileUpload;
    private boolean _hideButtons;

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

        VerticalPanel panel = new VerticalPanel();

        final FormPanel form = new FormPanel();

        String url = PropertyUtil.getRelativeURL("uploadFileForInferencing", "property");
        form.setAction(url);

        // skip display of a file upload form, the service will obtain the file from other means
        _hideFileUpload = Boolean.parseBoolean(PropertyUtil.getServerProperty("skipFileUpload"));

        form.setEncoding(FormPanel.ENCODING_MULTIPART);
        form.setMethod(FormPanel.METHOD_POST);

        form.addFormHandler(new UploadFormHandler());
        
        form.setWidget(panel);

        VerticalPanel uploadPanel = new VerticalPanel();

        if (!_hideFileUpload)
        {
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
        }
        uploadStatusLabel = new HTML("&nbsp;");

        panel.add(uploadStatusLabel);

        mainPanel = new VerticalPanel();
        mainPanel.add(form);
    }

    public Panel getMainPanel()
    {
        return mainPanel;
    }


    protected String getTypeURI()
    {
        return PropertyUtil.getServerProperty("typeURI");
    }


    public void finish()
    {
        if (!cancelRequested)
            onFinish();
    }

    protected void onFinish()
    {
        String successURL = PropertyUtil.getServerProperty("successURL");
        navigate(successURL);
    }

    protected void onCancel()
    {
        String cancelURL = PropertyUtil.getServerProperty("cancelURL");
        if (null == cancelURL || cancelURL.length() == 0)
            back();
        else
            navigate(cancelURL);
    }

    protected void importData()
    {
        service.getDomainDescriptor(getTypeURI(), new ErrorDialogAsyncCallback<GWTDomain>()
        {
            public void handleFailure(String message, Throwable caught)
            {
                onCancel();
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
        for (GWTPropertyDescriptor prop : grid.getColumns())
        {
            // Don't create properties for columns we're mapping, or that are already in the base table
            String propName = prop.getName();
            if (ignoredColumns.contains(propName) || baseColumnNames.contains(propName.toLowerCase()))
                continue;

            newProps.add(prop);
        }

        service.updateDomainDescriptor(domain, newDomain, new ErrorDialogAsyncCallback<List<String>>()
        {
            public void handleFailure(String message, Throwable caught)
            {
                onCancel();
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
        service.getDomainDescriptor(domain.getDomainURI(), new ErrorDialogAsyncCallback<GWTDomain>()
        {
            public void handleFailure(String message, Throwable caught)
            {
                onCancel();
            }

            public void onSuccess(GWTDomain result)
            {
                final GWTDomain newDomain = new GWTDomain(result);
                newDomain.getFields().clear();
                service.updateDomainDescriptor(result, newDomain, new ErrorDialogAsyncCallback<List<String>>()
                {
                    public void handleFailure(String message, Throwable caught)
                    {
                        onCancel();
                    }

                    public void onSuccess(List<String> errors){}
                });
            }
        });
    }

    /*
         The "import data" stage supports both synchronous and asynchronous imports. A synchronous service will finish
         the entire import and then return a status object indicating "complete." An asynchronous service will initiate
         the import in a background thread and return an "incomplete" status including a jobId. The client uses the
         jobId to query import progress periodically via a timer and to cancel the import if requested.
     */
    protected void importData(final GWTDomain domain)
    {
        Map<String, String> columnMap;

        if (columnMapper != null)
            columnMap = columnMapper.getColumnMap();
        else
            columnMap = new HashMap<String, String>(); // emptyMap() is not serializable

        service.importData(domain, columnMap, new ErrorDialogAsyncCallback<ImportStatus>()
        {
            public void handleFailure(String message, Throwable caught)
            {
                resetDomainFields(domain);
                onCancel();
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

    private Timer statusTimer;
    private String jobId;

    private void initProgressIndicator(final String jobId, final GWTDomain domain)
    {
        this.jobId = jobId;

        statusTimer = new Timer() {
            public void run()
            {
                service.getStatus(jobId, new ErrorDialogAsyncCallback<ImportStatus>()
                {
                    public void handleFailure(String message, Throwable caught)
                    {
                        resetDomainFields(domain);
                        onCancel();
                        cancel();
                    }

                    public void onSuccess(ImportStatus status)
                    {
                        updateStatus(status);

                        if (status.isComplete())
                        {
                            cancel();
                            handleComplete(status, domain);
                        }
                    }
                });
            }
        };

        statusTimer.scheduleRepeating(2000);
    }

    protected void displayInferredColumns(List<InferencedColumn> inferredColumns)
    {
        uploadStatusLabel.setHTML("&nbsp;");
        columns = inferredColumns;
        boolean needGridAndButtons = false;
        if (grid == null)
        {
            needGridAndButtons = true;
            grid = new DomainImportGrid(service, _domain);
            VerticalPanel gridPanel = new VerticalPanel();
            gridPanel.add(new HTML("Showing first " + columns.get(0).getData().size() + " rows (uncheck column checkboxes to ignore import):<p>"));
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

        if (needGridAndButtons && !_hideButtons)
        {
            HorizontalPanel buttons = new HorizontalPanel();
            importButton = new ImageButton("Import", new ClickHandler()
            {
                public void onClick(ClickEvent e)
                {
                    handleImport();
/*
                    importButton.setEnabled(false);
                    progressBarText = new ProgressBarText("Creating columns...");
                    progressBar = new ProgressBar(0, 100, 0, progressBarText);  // Placeholder to display the first couple messages
                    mainPanel.add(progressBar);
                    importData();
*/
                }
            });
            buttons.add(importButton);
            buttons.add(new ImageButton("Cancel", new ClickHandler()
            {
                public void onClick(ClickEvent e)
                {
                    handleCancel();
                }
            }));

            mainPanel.add(buttons);
        }
    }

    public void handleImport()
    {
        if (importButton != null)
            importButton.setEnabled(false);
        progressBarText = new ProgressBarText("Creating columns...");
        progressBar = new ProgressBar(0, 100, 0, progressBarText);  // Placeholder to display the first couple messages
        mainPanel.add(progressBar);
        importData();
    }

    private void updateStatus(ImportStatus status)
    {
        if (status.getTotalRows() > 0)
        {
            if (status.getTotalRows() != (int)progressBar.getMaxProgress())
            {
                progressBar.setMaxProgress(status.getTotalRows());
            }

            progressBar.setProgress(status.getCurrentRow());
        }
        else
        {
            // If we don't know the total number of rows we just update the text
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
        handleFailure(sb.toString());
    }

    private void handleFailure(String message)
    {
        Window.alert(message);
        onCancel();
    }

    public void handleCancel()
    {
        if (null == jobId)
        {
            onCancel();
        }
        else
        {
            cancelRequested = true;
            statusTimer.cancel();
            service.cancelImport(jobId, new ErrorDialogAsyncCallback<String>("Cancel failure") {
                public void onSuccess(String result)
                {
                    navigate(result);
                }
            });
        }
    }

    public void setHideButtons(boolean hideButtons)
    {
        _hideButtons = hideButtons;
    }

    public static native void navigate(String url) /*-{
        $wnd.location.href = url;
    }-*/;


    public static native void back() /*-{
        $wnd.history.back();
    }-*/;
    
    private class UploadFormHandler extends ErrorDialogAsyncCallback<List<InferencedColumn>> implements FormHandler
    {
        public void onSubmit(FormSubmitEvent event)
        {
            if (fileUpload.getFilename().length() == 0)
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

        public void handleFailure(String message, Throwable caught)
        {
            uploadStatusLabel.setHTML("&nbsp;");
        }

        public void onSuccess(List<InferencedColumn> result)
        {
            displayInferredColumns(result);
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

            add(new HTML("<br/>"));
            add(new HTML("<b>Column Mapping:</b>"));
            add(new InlineHTML("The list below are columns that already exist in the Domain and can be mapped with the " +
                    "inferred columns from the uploaded file.<br>Establish a mapping by selecting a column from the dropdown list to match " +
                    "the exising Domain column.<br>When the data is imported, the data from the inferred column will be added to the " +
                    "mapped Domain column.<br>To ignore a mapping, select '&lt;none&gt;' from the dropdown list.<br/><br/>"));

            Grid mappingGrid = new Grid(columnsToMap.size(), 3);
            add(mappingGrid);

            for (int row=0; row < columnsToMap.size(); row++)
            {
                String destinationColumn = columnsToMap.get(row);
                ListBox selector = new ListBox();
                selector.setName(destinationColumn);
                int rowToSelect = 0;

                selector.addItem("<none>", null);
                for (int inferencedIndex = 0; inferencedIndex < columns.size(); inferencedIndex++)
                {
                    InferencedColumn column = columns.get(inferencedIndex);
                    String name = column.getPropertyDescriptor().getName();
                    selector.addItem(name);
                    if (areColumnNamesEquivalent(name,destinationColumn))
                        rowToSelect = inferencedIndex + 1;
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
                String value = StringUtils.trimToNull(listBox.getValue(listBox.getSelectedIndex()));
                if (value != null)
                    columnNames.add(value);
            }
            return columnNames;
        }

        /**
         * Map of column in the file -> column in the database
         */
        public Map<String,String> getColumnMap()
        {
            Map<String, String> result = new HashMap<String, String>();

            for (int i = 0; i < columnsToMap.size(); i++)
            {
                String dataColumn = columnsToMap.get(i);
                ListBox selector = columnSelectors.get(i);
                String fileColumn = StringUtils.trimToNull(selector.getValue(selector.getSelectedIndex()));
                if (fileColumn != null)
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
