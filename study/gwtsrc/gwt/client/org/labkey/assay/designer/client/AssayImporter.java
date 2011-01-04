/*
 * Copyright (c) 2010 LabKey Corporation
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
import gwt.client.org.labkey.study.StudyApplication;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.BoundCheckBox;
import org.labkey.api.gwt.client.ui.BoundTextBox;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.WebPartPanel;
import org.labkey.api.gwt.client.ui.domain.DomainImporter;
import org.labkey.api.gwt.client.ui.domain.DomainImporterServiceAsync;
import org.labkey.api.gwt.client.ui.domain.InferencedColumn;
import org.labkey.api.gwt.client.util.BooleanProperty;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;
import org.labkey.api.gwt.client.util.StringProperty;
import org.labkey.api.gwt.client.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
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

    public void onModuleLoad()
    {
        RootPanel rootPanel = StudyApplication.getRootPanel();
        if (null == rootPanel)
            rootPanel = RootPanel.get("gwt.AssayDesigner-Root");

        _path = PropertyUtil.getServerProperty("path");
        _file = PropertyUtil.getServerProperty("file");
        _showInferredColumns = Boolean.valueOf(PropertyUtil.getServerProperty("showInferredColumns"));

        if (rootPanel != null)
        {
            addButtonBar(rootPanel);

            rootPanel.add(new HTML("<br/>"));
            addAssayWebPart(rootPanel);

            if (_showInferredColumns)
            {
                rootPanel.add(new HTML("<br/>"));
                addImporterWebPart(rootPanel);
            }
        }
    }

    private void addButtonBar(RootPanel root)
    {
        HorizontalPanel btnBar = new HorizontalPanel();
        btnBar.add(new ImageButton("Next", new ClickHandler(){
            public void onClick(ClickEvent event)
            {
                String assayName = _assayName != null ? _assayName.getString() : null;

                if (StringUtils.isEmpty(assayName))
                {
                    Window.alert("Please enter a valid assay name.");
                    return;
                }

                // on import create the protocol using the assay name specified
                getService().createProtocol(PropertyUtil.getServerProperty("providerName"), _assayName.getString(), new ErrorDialogAsyncCallback<GWTProtocol>()
                {
                    public void handleFailure(String message, Throwable caught)
                    {
                    }

                    public void onSuccess(GWTProtocol protocol)
                    {
                        _protocol = protocol;

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
        }));
        btnBar.add(new ImageButton("Cancel", new ClickHandler(){
            public void onClick(ClickEvent event)
            {
                onCancel();
/*
                if (_domainImporter != null)
                    _domainImporter.handleCancel();
*/
            }
        }));

        root.add(btnBar);
    }

    private void addAssayWebPart(RootPanel root)
    {
        VerticalPanel panel = new VerticalPanel();

        int row = 0;
        FlexTable table = new FlexTable();
        BoundTextBox name = new BoundTextBox("Assay Name", "assay_name_id", _assayName);
        name.setRequired(true);
        table.setWidget(row, 0, new InlineHTML("Name&nbsp;(Required)&nbsp;"));
        table.setWidget(row++, 1, name);

        BoundCheckBox showEditor = new BoundCheckBox("show_assay_editor_id", _showEditor, null);
        FlowPanel namePanel = new FlowPanel();
        namePanel.add(new InlineHTML("Show Advanced Assay Designer&nbsp;"));
        namePanel.add(new HelpPopup("Advanced Assay Designer", "This wizard allows you to quickly design an assay based on the columns in " +
                "a spreadsheet or text file. If you want to define other custom columns check this box and the advanced " +
                "assay designer will be displayed after the next button is clicked."));
        table.setWidget(row, 0, namePanel);
        table.setWidget(row++, 1, showEditor);

        WebPartPanel infoPanel = new WebPartPanel("Assay Properties", panel);
        infoPanel.setWidth("100%");
        root.add(infoPanel);

        panel.add(table);
    }

    private void addImporterWebPart(RootPanel root)
    {
        Set<String> baseColumnNames = new HashSet<String>();
        String baseColNamesString = PropertyUtil.getServerProperty("baseColumnNames");
        if (baseColNamesString != null)
        {
            String[] baseColArray = baseColNamesString.split(",");
            for (String s : baseColArray)
                baseColumnNames.add(s);
        }

        VerticalPanel panel = new VerticalPanel();
        WebPartPanel columnsPanel = new WebPartPanel("Columns for Assay Data", panel);
        columnsPanel.setWidth("100%");
        root.add(columnsPanel);

        // create an importer instance, we will manage the buttons externally
        _domainImporter = new AssayDomainImporter(getService(), Collections.<String>emptyList(), baseColumnNames);
        _domainImporter.setHideButtons(true);

        HTML title = new HTML("Columns for Assay Data");
        title.setStyleName("labkey-wp-title");

        //vPanel.add(title);
        panel.add(new InlineHTML("These columns have been inferred from the uploaded file: <b>" + _file + "</b> and will be created as part of the assay definition. " +
                "Uncheck the columns to ignore during the " +
                "column creation step.<br/>"));

        panel.add(_domainImporter.getMainPanel());
    }

    private AssayImporterServiceAsync getService()
    {
        if (service == null)
        {
            service = (AssayImporterServiceAsync) GWT.create(AssayImporterService.class);
            ServiceUtil.configureEndpoint(service, "assayImportService");
        }
        return service;
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
        String cancelURL = PropertyUtil.getServerProperty("cancelURL");
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

    private class AssayDomainImporter extends DomainImporter
    {
        private String _typeURI;

        public AssayDomainImporter(AssayImporterServiceAsync service, List<String> columnsToMap, Set<String> baseColumnNames)
        {
            super((DomainImporterServiceAsync)service, columnsToMap, baseColumnNames);

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
        protected void importData(GWTDomain domain)
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
