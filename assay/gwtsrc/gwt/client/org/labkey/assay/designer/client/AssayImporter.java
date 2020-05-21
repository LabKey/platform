/*
 * Copyright (c) 2011-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gwt.client.org.labkey.assay.designer.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.sencha.gxt.cell.core.client.form.ComboBoxCell;
import com.sencha.gxt.widget.core.client.form.SimpleComboBox;
import gwt.client.org.labkey.assay.AssayApplication;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.BoundTextBox;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.WebPartPanel;
import org.labkey.api.gwt.client.ui.domain.DomainImporter;
import org.labkey.api.gwt.client.ui.domain.InferencedColumn;
import org.labkey.api.gwt.client.util.BooleanProperty;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;
import org.labkey.api.gwt.client.util.StringProperty;
import org.labkey.api.gwt.client.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: Dec 10, 2010
 * Time: 1:28:17 PM
 */
public class AssayImporter implements EntryPoint
{
    private AssayDomainImporter _domainImporter;
    private String _path;
    private String _file;
    private boolean _showInferredColumns;
    private StringProperty _assayName = new StringProperty();
    private BooleanProperty _showEditor = new BooleanProperty();
    private AssayImporterServiceAsync service = null;
    private GWTProtocol _protocol;
    private String _providerName;
    private RootPanel _rootPanel;
    private String _containerId;

    public void onModuleLoad()
    {
        _rootPanel = AssayApplication.getRootPanel();
        if (null == _rootPanel)
            _rootPanel = RootPanel.get("gwt.AssayDesigner-Root");

        _path = PropertyUtil.getServerProperty("path");
        _file = PropertyUtil.getServerProperty("file");
        _showInferredColumns = Boolean.valueOf(PropertyUtil.getServerProperty("showInferredColumns"));
        _providerName = PropertyUtil.getServerProperty("providerName");

        if (_rootPanel != null)
        {
            addButtonBar(_rootPanel);

            _rootPanel.add(new HTML("<br/>"));
            addAssayWebPart(_rootPanel);

            if (_showInferredColumns)
            {
                getService().getBaseColumns(_providerName, new ErrorDialogAsyncCallback<List<GWTPropertyDescriptor>>()
                {
                    public void handleFailure(String message, Throwable caught)
                    {
                        onCancel();
                    }

                    public void onSuccess(List<GWTPropertyDescriptor> baseColumns)
                    {
                        _rootPanel.add(new HTML("<br/>"));
                        addImporterWebPart(_rootPanel, baseColumns);
                    }
                });
            }
        }
    }

    private void addButtonBar(RootPanel root)
    {
        HorizontalPanel btnBar = new HorizontalPanel();
        btnBar.add(new ImageButton("Begin import", new ClickHandler(){
            public void onClick(ClickEvent event)
            {
                String assayName = _assayName != null ? _assayName.getString() : null;

                if (StringUtils.isEmpty(assayName))
                {
                    Window.alert("Please enter a valid assay name.");
                    return;
                }
                onNext(false);
            }
        }));
        btnBar.add(new ImageButton("Show Assay Designer", new ClickHandler(){
            public void onClick(ClickEvent event)
            {
                String assayName = _assayName != null ? _assayName.getString() : null;

                if (StringUtils.isEmpty(assayName))
                {
                    Window.alert("Please enter a valid assay name.");
                    return;
                }

                onNext(true);
            }
        }));
        btnBar.add(new ImageButton("Cancel", new ClickHandler(){
            public void onClick(ClickEvent event)
            {
                onCancel();
            }
        }));

        root.add(btnBar);
    }

    private void addAssayWebPart(final RootPanel root)
    {
        getService().getAssayLocations(new ErrorDialogAsyncCallback<List<Map<String, String>>>()
        {
            public void handleFailure(String message, Throwable caught)
            {
                onCancel();
            }

            public void onSuccess(List<Map<String, String>> locations)
            {
                VerticalPanel panel = new VerticalPanel();

                int row = 0;
                FlexTable table = new FlexTable();
                BoundTextBox name = new BoundTextBox("Assay Name", "AssayDesignerName", _assayName);
                name.setRequired(true);
                table.setWidget(row, 0, new InlineHTML("Name&nbsp;(Required)&nbsp;"));
                table.setWidget(row++, 1, name);

                AssayLocation defaultLocation = null;
                SimpleComboBox<AssayLocation> combo = new SimpleComboBox<AssayLocation>(item -> item.getLabel());

                combo.setEditable(false);
                combo.setEmptyText("No location");
                combo.setWidth(250);
                for (Map<String, String> location : locations)
                {
                    AssayLocation aLoc = new AssayLocation(location.get("id"), location.get("label"));
                    combo.add(aLoc);
                    if (Boolean.parseBoolean(location.get("default")))
                        defaultLocation = aLoc;
                }

                if (defaultLocation != null)
                {
                    _containerId = defaultLocation.getId();
                    combo.setValue(defaultLocation);
                }
                combo.setTriggerAction(ComboBoxCell.TriggerAction.ALL);
                combo.addValueChangeHandler(event -> {
                    AssayLocation value = event.getValue();
                    if (value != null)
                        _containerId = value.getId();
                });

                FlowPanel locationPanel = new FlowPanel();
                locationPanel.add(new InlineHTML("Location&nbsp;"));
                locationPanel.add(new HelpPopup("Assay Location", "Create the assay in a project or shared folder so it is visible in subfolders."));
                table.setWidget(row, 0, locationPanel);
                table.setWidget(row++, 1, combo);

                WebPartPanel infoPanel = new WebPartPanel("Assay Properties", panel);
                root.add(infoPanel);

                panel.add(table);
            }
        });
    }

    private void addImporterWebPart(RootPanel root, List<GWTPropertyDescriptor> baseColumns)
    {
        Set<String> baseColumnNames = new HashSet<String>();
        List<String> columnsToMap = new ArrayList<String>();
        
        for (GWTPropertyDescriptor prop : baseColumns)
        {
            baseColumnNames.add(prop.getName());
            columnsToMap.add(prop.getName());
        }

        VerticalPanel panel = new VerticalPanel();
        WebPartPanel columnsPanel = new WebPartPanel("Columns for Assay Data", panel);
        root.add(columnsPanel);

        // create an importer instance, we will manage the buttons externally
        _domainImporter = new AssayDomainImporter(getService(), columnsToMap, baseColumnNames);
        _domainImporter.setHideButtons(true);
        _domainImporter.setColumnsToMap(baseColumns);

        HTML title = new HTML("Columns for Assay Data");
        title.setStyleName("labkey-wp-title");

        //vPanel.add(title);
        panel.add(new InlineHTML("These columns have been inferred from the uploaded file, <b>"
                + _file + "</b>, and will be created as part of the assay definition."));

        panel.add(_domainImporter.getMainPanel());
    }

    private AssayImporterServiceAsync getService()
    {
        if (service == null)
        {
            service = GWT.create(AssayImporterService.class);
            ServiceUtil.configureEndpoint(service, "assayImportService");
        }
        return service;
    }

    /**
     * Handler for starting the import or moving to the assay designer. Column settings are validated
     * against the entire document before creating the protocol and setting up the data domain.
     * @param showAssayDesigner - true to show the advanced assay designer, else start importing the data.
     */
    private void onNext(final boolean showAssayDesigner)
    {
        // validate the selected column descriptor
        List<InferencedColumn> inferencedColumns = new ArrayList<InferencedColumn>();
        if (_domainImporter != null)
        {
            for (GWTPropertyDescriptor prop : _domainImporter.getColumns(true))
            {
                InferencedColumn col = new InferencedColumn();

                col.setPropertyDescriptor(prop);
                inferencedColumns.add(col);
            }
        }

        getService().validateColumns(inferencedColumns, _path, _file, new ErrorDialogAsyncCallback<Boolean>()
        {
            public void onSuccess(Boolean result)
            {
                // on import create the protocol using the assay name specified
                getService().createProtocol(PropertyUtil.getServerProperty("providerName"), _assayName.getString(), _containerId,  new ErrorDialogAsyncCallback<GWTProtocol>()
                {
                    public void handleFailure(String message, Throwable caught)
                    {
                    }

                    public void onSuccess(GWTProtocol protocol)
                    {
                        _protocol = protocol;
                        _showEditor.setBool(showAssayDesigner);


                        if (_domainImporter != null)
                        {
                            // create the columns on the server then redirect to the assay import
                            _domainImporter.handleImport();
                        }
                        else
                            // just redirect to the assay import
                            gotoProtocolImport();
                    }
                });
            }

            public void handleFailure(String message, Throwable caught)
            {
                // do nothing, we will already show the error in a popup
            }
        });
    }

    private void gotoProtocolImport()
    {
        if (_protocol != null)
        {
            // if the user has specified to show the advanced assay designer after the columns have been created
            // then navigate to the designer, else navigate to the protocol specific import action
            if (_showEditor.booleanValue())
            {
                getService().getDesignerURL(_protocol, _path, _file, new ErrorDialogAsyncCallback<String>()
                {
                    public void handleFailure(String message, Throwable caught)
                    {
                        onCancel();
                    }

                    public void onSuccess(String importURL)
                    {
                        navigate(importURL);
                    }
                });
            }
            else
            {
                getService().getImportURL(_protocol, _path, _file, new ErrorDialogAsyncCallback<String>()
                {
                    public void handleFailure(String message, Throwable caught)
                    {
                        onCancel();
                    }

                    public void onSuccess(String importURL)
                    {
                        navigate(importURL);
                    }
                });
            }
        }
    }

    protected void onCancel()
    {
        String cancelURL = PropertyUtil.getCancelURL();
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

    public static class AssayLocation
    {
        private String _id;
        private String _label;
        public AssayLocation(String id, String label)
        {
            setId(id);
            setLabel(label);
        }

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public String getId()
        {
            return _id;
        }

        public void setId(String id)
        {
            _id = id;
        }
    }

    private class AssayDomainImporter extends DomainImporter
    {
        private String _typeURI;

        public AssayDomainImporter(AssayImporterServiceAsync service, List<String> columnsToMap, Set<String> baseColumnNames)
        {
            super(service, columnsToMap, baseColumnNames);

            service.getInferenceColumns(PropertyUtil.getServerProperty("path"), PropertyUtil.getServerProperty("file"), new ErrorDialogAsyncCallback<List<InferencedColumn>>()
            {
                public void handleFailure(String message, Throwable caught)
                {
                    onCancel();
                }

                public void onSuccess(List<InferencedColumn> result)
                {
                    displayInferredColumns(result);
                }
            });
        }

        @Override
        protected void importData()
        {
            // if this is not an existing domain, ask the service to create one using the assay name specified
            if (getTypeURI() == null && _protocol != null)
            {
                service.getDomainImportURI(_protocol, new ErrorDialogAsyncCallback<String>()
                {
                    public void handleFailure(String message, Throwable caught)
                    {
                        onCancel();
                    }

                    public void onSuccess(String typeURI)
                    {
                        _typeURI = typeURI;
                        _importData();
                    }
                });
            }
            else
                _importData();
        }

        private void _importData()
        {
            super.importData();
        }

        /**
         * Method to perform the actual data import of the inferred data after the columns have been created on the
         * server. Override this for the assay case because assay data import needs to handle multiple wizard
         * steps on a provider specific basis.
         */
        @Override
        protected void onFinish()
        {
            gotoProtocolImport();
        }

        @Override
        protected String getTypeURI()
        {
            if (_typeURI != null)
                return _typeURI;
            else
                return super.getTypeURI();
        }
    }
}
