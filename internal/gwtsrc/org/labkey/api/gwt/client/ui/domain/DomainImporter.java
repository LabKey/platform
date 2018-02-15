/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import com.extjs.gxt.ui.client.widget.form.SimpleComboBox;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.ChangeListener;
import com.google.gwt.user.client.ui.FormHandler;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormSubmitCompleteEvent;
import com.google.gwt.user.client.ui.FormSubmitEvent;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Hidden;
import com.google.gwt.user.client.ui.HorizontalPanel;
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
import org.labkey.api.gwt.client.util.ServiceUtil;

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
    private List<GWTPropertyDescriptor> columnsToMapInfo = new ArrayList<GWTPropertyDescriptor>();

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
    protected DomainImportGrid<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor> grid;

    private boolean cancelRequested = false;
    private boolean _hideFileUpload;
    private boolean _hideButtons;
    private ImageButton _cancelButton;

    public DomainImporter(DomainImporterServiceAsync service, List<String> columnsToMap, Set<String> baseColumnNames)
    {
        this(service, columnsToMap, baseColumnNames, new ArrayList<GWTPropertyDescriptor>());
    }

    public DomainImporter(DomainImporterServiceAsync service, List<String> columnsToMap, Set<String> baseColumnNames, List<GWTPropertyDescriptor> baseColumnMetadata)
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
        Hidden hidden = new Hidden("X-LABKEY-CSRF", ServiceUtil.getCsrfToken());
        panel.add(hidden);

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

        if (PropertyUtil.getCancelURL() != null)
        {
            _cancelButton = new ImageButton("Cancel", new ClickHandler()
            {
                public void onClick(ClickEvent e)
                {
                    handleCancel();
                }
            });

            mainPanel.add(_cancelButton);
        }
        setColumnsToMap(baseColumnMetadata);
    }

    /**
     * @Deprecated temporary method to push in extra metadata for the mapped columns, in the next
     * release we need to remove this method and make it so mapped columns come in as
     * GWTPropertyDescriptors not just names.
     */
    public void setColumnsToMap(List<GWTPropertyDescriptor> extraInfo)
    {
        columnsToMapInfo = extraInfo;
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
        String successURL = PropertyUtil.getReturnURL();
        navigate(successURL);
    }

    protected void onCancel()
    {
        String cancelURL = PropertyUtil.getCancelURL();
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
        DomainImportGrid.ColumnMapper columnMapper = grid.getColumnMapper();
        Set<String> ignoredColumns;

        if (columnMapper != null)
            ignoredColumns = columnMapper.getMappedColumnNames();
        else
            ignoredColumns = new HashSet<String>(); // emptySet is not serializable

        List<GWTPropertyDescriptor> newProps = newDomain.getFields();
        for (GWTPropertyDescriptor prop : grid.getColumns(false))
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
        resetDomainFields(domain, null);
    }

    private void resetDomainFields(GWTDomain domain, final AsyncCallback<List<String>> callback)
    {
        service.getDomainDescriptor(domain.getDomainURI(), new ErrorDialogAsyncCallback<GWTDomain>()
        {
            public void handleFailure(String message, Throwable caught)
            {
                onCancel();
                if(callback != null)
                    callback.onFailure(caught);
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
                        if(callback != null)
                            callback.onFailure(caught);
                    }

                    public void onSuccess(List<String> errors){
                        if(callback != null)
                            callback.onSuccess(errors);
                    }
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
        DomainImportGrid.ColumnMapper columnMapper = grid.getColumnMapper();
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
            gridPanel.add(new HTML("Uncheck a column to ignore it during import.<br/>"
                    + "Showing first " + columns.get(0).getData().size() + " rows:<p>"));
            gridPanel.add(grid);
            mainPanel.add(gridPanel);
        }
        grid.setColumns(columns);
        if (!needGridAndButtons && needToMapColumns)
        {
            // We've already been through here once, remove our old mapper
            grid.removeColumnMapper();
        }
        if (needToMapColumns)
        {
            grid.addColumnMapper(columnsToMap, columnsToMapInfo);
        }

        if (needGridAndButtons && !_hideButtons)
        {
            HorizontalPanel buttons = new HorizontalPanel();
            importButton = new ImageButton("Import", new ClickHandler()
            {
                public void onClick(ClickEvent e)
                {
                    if (doColumnsHaveDups())
                        Window.alert("Columns for import contain duplicate column names. Please change one of the column names in the import data. (Unselecting a column is not sufficient.)");
                    else if (areAllServerColumnsSelected() == false )
                        Window.alert("You must select all required Server Columns before importing.");
                    else
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

    private boolean doColumnsHaveDups()
    {
        Set<String> lowercaseNames = new HashSet();
        for (GWTPropertyDescriptor prop : grid.getColumns(true))        // Issue 19126 (dave): should do getColumns(false), but other problems (yet to be figured out) prevent simple unselecting from letting user import
        {
            String lowercaseName = prop.getName().toLowerCase();
            if (lowercaseNames.contains(lowercaseName))
                return true;
            lowercaseNames.add(lowercaseName);
        }
        return false;
    }

    public boolean areAllServerColumnsSelected()
    {
        boolean columnsSelected = true;
        if (null != grid.getColumnMapper())
            for ( SimpleComboBox<String> column : grid.getColumnMapper()._columnSelectors )
                if (column.getSelectedIndex() == -1 )
                    columnsSelected = false;
        return columnsSelected;
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

    public List<GWTPropertyDescriptor> getColumns(boolean includeIgnored)
    {
        return grid.getColumns(includeIgnored);
    }

    public boolean isImportEnabled(GWTPropertyDescriptor prop)
    {
        return grid.isImportEnabled(prop);
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
        final List<String> errors = status.getMessages();

        if (errors == null || errors.isEmpty())
        {
            finish();
        }
        else
        {
            resetDomainFields(domain, new AsyncCallback<List<String>>()
            {
                public void onFailure(Throwable caught){}

                public void onSuccess(List<String> result)
                {
                    handleServerFailure(errors);
                }
            });

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
        // reEnable page for retry of import
        importButton.setEnabled(true);
        mainPanel.remove(progressBar);
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
            if (event.getResults() != null && event.getResults().toLowerCase().contains("success"))
            {
                uploadStatusLabel.setText("Processing...");
                service.inferenceColumns(this);
            }
            else
            {
                uploadStatusLabel.setHTML("&nbsp;");
                Window.alert("File upload failed. " + (event.getResults() == null ? "" : event.getResults()));
            }
        }

        public void handleFailure(String message, Throwable caught)
        {
            uploadStatusLabel.setHTML("&nbsp;");
        }

        public void onSuccess(List<InferencedColumn> result)
        {
            if (_cancelButton != null)
                mainPanel.remove(_cancelButton);
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
}
