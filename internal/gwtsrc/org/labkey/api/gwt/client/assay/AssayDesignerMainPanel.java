/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

package org.labkey.api.gwt.client.assay;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import org.labkey.api.gwt.client.AbstractDesignerMainPanel;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTContainer;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.BoundCheckBox;
import org.labkey.api.gwt.client.ui.BoundTextAreaBox;
import org.labkey.api.gwt.client.ui.BoundTextBox;
import org.labkey.api.gwt.client.ui.DirtyCallback;
import org.labkey.api.gwt.client.ui.FontButton;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.LinkButton;
import org.labkey.api.gwt.client.ui.PropertiesEditor;
import org.labkey.api.gwt.client.ui.SaveButtonBar;
import org.labkey.api.gwt.client.ui.Saveable;
import org.labkey.api.gwt.client.ui.WebPartPanel;
import org.labkey.api.gwt.client.ui.WidgetUpdatable;
import org.labkey.api.gwt.client.ui.WindowUtil;
import org.labkey.api.gwt.client.util.BooleanProperty;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.FlexTableRowDragController;
import org.labkey.api.gwt.client.util.FlexTableRowDropController;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;
import org.labkey.api.gwt.client.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * User: brittp
 * Date: Jun 20, 2007
 * Time: 2:24:04 PM
 */
public class AssayDesignerMainPanel extends AbstractDesignerMainPanel implements Saveable<GWTProtocol>, DirtyCallback
{
    private AssayServiceAsync _testService;
    private final String _providerName;
    private Integer _protocolId;
    protected GWTProtocol _assay;
    private BoundTextBox _nameBox;
    private CheckBox _autoCopyCheckBox;
    private ListBox _autoCopyTargetListBox;
    private boolean _copy;
    private HandlerRegistration _closeHandlerManager;
    private List<GWTContainer> _autoCopyTargets;
    private boolean _isPlateBased;
    private BooleanProperty _debugScriptFiles = new BooleanProperty(false);
    private BooleanProperty _editableRuns = new BooleanProperty(false);
    private BooleanProperty _editableResults = new BooleanProperty(false);
    private BooleanProperty _backgroundUpload = new BooleanProperty(false);
    private FlexTable _transformScriptTable;
    private boolean _allowSpacesInPath;

    private static final int TRANSFORM_SCRIPT_PATH_COLUMN_INDEX = 0;
    private static final int TRANSFORM_SCRIPT_DRAG_LABEL_COLUMN_INDEX = 2;
    private static final int TRANSFORM_SCRIPT_REMOVE_BUTTON_COLUMN_INDEX = 1;

    public AssayDesignerMainPanel(RootPanel rootPanel)
    {
        super(rootPanel);

        String protocolIdStr = PropertyUtil.getServerProperty("protocolId");
        _protocolId = protocolIdStr != null ? new Integer(Integer.parseInt(protocolIdStr)) : null;
        _providerName = PropertyUtil.getServerProperty("providerName");
        String copyStr = PropertyUtil.getServerProperty("copy");
        _copy = copyStr != null && Boolean.TRUE.toString().equals(copyStr);

        _autoCopyTargetListBox = new ListBox();
        DOM.setStyleAttribute(_autoCopyTargetListBox.getElement(), "width", "500px");
        DOM.setElementAttribute(_autoCopyTargetListBox.getElement(), "id", "autoCopyTarget");
        _autoCopyTargetListBox.setEnabled(false);

        _autoCopyCheckBox = new CheckBox();
        _autoCopyCheckBox.getElement().setId("auto-copy-checkbox");
        _autoCopyCheckBox.setName("autoCopy");
        _autoCopyCheckBox.setEnabled(false);

        _allowSpacesInPath = !PropertyUtil.getServerProperty("osName").contains("linux");
    }

    public void showAsync()
    {
        _rootPanel.clear();
        _rootPanel.add(new Label("Loading..."));
        if (_protocolId != null)
        {
            getService().getAssayDefinition(_protocolId.intValue(), _copy, new AsyncCallback<GWTProtocol>()
            {
                public void onFailure(Throwable throwable)
                {
                    addErrorMessage("Unable to load assay definition: " + throwable.getMessage());
                }

                public void onSuccess(GWTProtocol assay)
                {
                    show(assay);
                }
            });
        }
        else
        {
            getService().getAssayTemplate(_providerName, new AsyncCallback<GWTProtocol>()
            {
                public void onFailure(Throwable throwable)
                {
                    addErrorMessage("Unable to load assay template: " + throwable.getMessage());
                }

                public void onSuccess(GWTProtocol assay)
                {
                    show(assay);
                }
            });
        }

        getService().getStudyContainers(new AsyncCallback<List<GWTContainer>>()
        {
            public void onFailure(Throwable throwable)
            {
                addErrorMessage("Unable to fetch list of studies for auto-copy : " + throwable.getMessage());
            }

            public void onSuccess(List<GWTContainer> result)
            {
                _autoCopyTargetListBox.clear();
                _autoCopyTargets = result;
                _autoCopyTargetListBox.addItem("", "");
                for (GWTContainer gwtContainer : result)
                {
                    _autoCopyTargetListBox.addItem(gwtContainer.getPath(), gwtContainer.getEntityId());
                }
                _autoCopyCheckBox.setEnabled(true);
                syncAutoCopy();
            }
        });
    }

    private void syncAutoCopy()
    {
        // Ensure that we've received all the async data we need to show the full UI
        if (_autoCopyCheckBox.isEnabled() && _assay != null)
        {
            if (_assay.getAutoCopyTargetContainer() != null)
            {
                _autoCopyCheckBox.setValue(true);
                _autoCopyTargetListBox.setEnabled(true);
                _autoCopyTargetListBox.setSelectedIndex(0);
                for (int i = 0; i < _autoCopyTargetListBox.getItemCount(); i++)
                {
                    if (_assay.getAutoCopyTargetContainer().getEntityId().equals(_autoCopyTargetListBox.getValue(i)))
                    {
                        _autoCopyTargetListBox.setSelectedIndex(i);
                        break;
                    }
                }
            }
            else
            {
                _autoCopyCheckBox.setValue(false);
                _autoCopyTargetListBox.setSelectedIndex(0);
                _autoCopyTargetListBox.setEnabled(false);
            }
        }
    }

    private AssayServiceAsync getService()
    {
        if (_testService == null)
        {
            _testService = GWT.create(AssayService.class);
            ServiceUtil.configureEndpoint(_testService, "service", "assay");
        }
        return _testService;
    }

    private void show(GWTProtocol assay)
    {
        _assay = assay;

        _rootPanel.clear();
        _domainEditors.clear();
        saveBarTop = new SaveButtonBar(this);
        _rootPanel.add(saveBarTop);
        _rootPanel.add(_statusLabel);

        FlexTable table = createAssayInfoTable(_assay);
        table.setStyleName("lk-fields-table");
        WebPartPanel infoPanel = new WebPartPanel("Assay Properties", table);
        _rootPanel.add(infoPanel);

        for (int i = 0; i < _assay.getDomains().size(); i++)
        {
            GWTDomain<GWTPropertyDescriptor> domain = _assay.getDomains().get(i);

            PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor> editor =
                    new PropertiesEditor.PD(_rootPanel, new DomainProtocolSaveable(this, domain), getService());
            editor.addChangeHandler(new ChangeHandler()
            {
                public void onChange(ChangeEvent e)
                {
                    setDirty(true);
                }
            });

            editor.init(domain);
            _domainEditors.add(editor);

            VerticalPanel vPanel = new VerticalPanel();
            if (domain.getDescription() != null)
            {
                vPanel.add(new Label(domain.getDescription()));
            }
            vPanel.add(editor.getWidget());

            final WebPartPanel panel = new WebPartPanel(domain.getName(), vPanel);
            panel.setWidth("100%");
            _rootPanel.add(panel);
        }

        saveBarBottom = new SaveButtonBar(this);
        _rootPanel.add(saveBarBottom);

        // When first creating an assay, the name will be empty. If that's the case, we need to
        // set the dirty bit
        if ("".equals(_nameBox.getBox().getText()))
            setDirty(true);
        else
            setDirty(_copy);

        if (_closeHandlerManager == null)
        {
            _closeHandlerManager = Window.addWindowClosingHandler(new DesignerClosingListener());
        }

        syncAutoCopy();
    }

    private class DomainProtocolSaveable implements Saveable<GWTDomain>
    {
        private GWTDomain _domain;
        private Saveable<GWTProtocol> _protocolSavable;

        public DomainProtocolSaveable(Saveable<GWTProtocol> protocolSavable, GWTDomain domain)
        {
            _protocolSavable = protocolSavable;
            _domain = domain;
        }

        public String getCurrentURL()
        {
            return _designerURL;
        }

        public void save()
        {
            _protocolSavable.save();
        }

        public void save(final SaveListener<GWTDomain> gwtDomainSaveListener)
        {
            _protocolSavable.save(new SaveListener<GWTProtocol>()
            {
                public void saveSuccessful(GWTProtocol result, String designerURL)
                {
                    GWTDomain match = null;
                    if (_domain.getDomainId() == 0)
                    {
                        // special case handling for the first save:
                        for (Iterator<GWTDomain<GWTPropertyDescriptor>> it = result.getDomains().iterator(); it.hasNext() && match == null;)
                        {
                            GWTDomain domain = it.next();
                            if (domain.getName().endsWith(_domain.getName()))
                                match = domain;
                        }
                    }
                    else
                    {
                        // if this is not the first save, we can use the domain ID to find the new domain:
                        for (Iterator<GWTDomain<GWTPropertyDescriptor>> it = result.getDomains().iterator(); it.hasNext() && match == null;)
                        {
                            GWTDomain domain = it.next();
                            if (domain.getDomainId() == _domain.getDomainId())
                                match = domain;
                        }
                    }
                    gwtDomainSaveListener.saveSuccessful(match, designerURL);
                }
            });
        }

        public void cancel()
        {
            _protocolSavable.cancel();
        }

        public void finish()
        {
            _protocolSavable.finish();
        }

        public boolean isDirty()
        {
            return _protocolSavable.isDirty();
        }
    }

    protected FlexTable createAssayInfoTable(final GWTProtocol assay)
    {
        final FlexTable table = new FlexTable();
        final String assayName = assay.getProtocolId() != null ? assay.getName() : null;
        int row = 0;

        _nameBox = new BoundTextBox("Name", "AssayDesignerName", assayName, new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                assay.setName(((TextBox) widget).getText());
                if (!((TextBox) widget).getText().equals(assayName))
                {
                    setDirty(true);
                }
            }
        }, this);
        _nameBox.setRequired(true);
        table.getFlexCellFormatter().setStyleName(row, 0, "labkey-form-label");
        if (assay.getProtocolId() == null || _copy)
        {
            table.setWidget(row, 1, _nameBox);
            table.setHTML(row++, 0, "Name (Required)");
        }
        else
        {
            table.setWidget(row, 1, new Label(assayName));
            table.setHTML(row++, 0, "Name");
        }

        BoundTextAreaBox descriptionBox = new BoundTextAreaBox("Description", "AssayDesignerDescription", assay.getDescription(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                if (!((TextArea) widget).getText().equals(assay.getDescription()))
                {
                    setDirty(true);
                }
                assay.setDescription(((TextArea) widget).getText());
            }
        }, this);
        table.setHTML(row, 0, "Description");
        table.getFlexCellFormatter().setStyleName(row, 0, "labkey-form-label");
        table.setWidget(row++, 1, descriptionBox);

        _autoCopyCheckBox.addValueChangeHandler(new ValueChangeHandler<Boolean>()
        {
            public void onValueChange(ValueChangeEvent<Boolean> event)
            {
                setDirty(true);
                _autoCopyTargetListBox.setEnabled(event.getValue().booleanValue() && hasAutoCopyTargets());
            }
        });
        _autoCopyTargetListBox.addChangeHandler(new ChangeHandler()
        {
            public void onChange(ChangeEvent event)
            {
                setDirty(true);
            }
        });

        FlowPanel autoCopyCheckboxPanel = new FlowPanel();
        autoCopyCheckboxPanel.add(new InlineHTML("Auto-copy Data"));
        autoCopyCheckboxPanel.add(new HelpPopup("Auto-copy Data", "When new runs are imported, automatically copy " +
                "their data rows to the specified target study. Only rows that include subject and visit/date " +
                "information will be copied."));
        table.getFlexCellFormatter().setStyleName(row, 0, "labkey-form-label");

        table.setWidget(row, 0, autoCopyCheckboxPanel);
        table.setWidget(row++, 1, _autoCopyCheckBox);

        FlowPanel autoCopyTargetPanel = new FlowPanel();
        autoCopyTargetPanel.add(new InlineHTML("Auto-copy Target"));
        autoCopyTargetPanel.add(new HelpPopup("Auto-copy Target", "If auto-copy is enabled, the target study to which " +
                "the data rows will be copied. The user performing the import must have insert permission in the target " +
                "study and the corresponding dataset."));
        table.getFlexCellFormatter().setStyleName(row, 0, "labkey-form-label");

        table.setWidget(row, 0, autoCopyTargetPanel);
        table.setWidget(row++, 1, _autoCopyTargetListBox);

        if (assay.getAvailablePlateTemplates() != null)
        {
            _isPlateBased = true;
            table.setHTML(row, 0, "Plate Template");
            final ListBox templateList = new ListBox();
            int selectedIndex = -1;
            for (int i = 0; i < assay.getAvailablePlateTemplates().size(); i++)
            {
                String current = assay.getAvailablePlateTemplates().get(i);
                templateList.addItem(current);
                if (current.equals(assay.getSelectedPlateTemplate()))
                    selectedIndex = i;
            }
            if (selectedIndex >= 0)
                templateList.setSelectedIndex(selectedIndex);
            if (templateList.getItemCount() > 0)
                assay.setSelectedPlateTemplate(templateList.getValue(templateList.getSelectedIndex()));
            templateList.addChangeHandler(new ChangeHandler()
            {
                public void onChange(ChangeEvent event)
                {
                    assay.setSelectedPlateTemplate(templateList.getValue(templateList.getSelectedIndex()));
                    setDirty(true);
                }
            });
            DOM.setElementAttribute(templateList.getElement(), "id", "plateTemplate");
            HorizontalPanel picker = new HorizontalPanel();
            picker.add(templateList);
            picker.add(new LinkButton("configure templates", PropertyUtil.getRelativeURL("plateTemplateList", "Plate")));

            picker.setVerticalAlignment(ALIGN_BOTTOM);
            table.getFlexCellFormatter().setStyleName(row, 0, "labkey-form-label");
            table.setWidget(row++, 1, picker);
        }

        if (assay.getAvailableDetectionMethods() != null)
        {
            table.setHTML(row, 0, "Detection Methods");
            final ListBox templateList = new ListBox();
            int selectedIndex = -1;
            for (int i = 0; i < assay.getAvailableDetectionMethods().size(); i++)
            {
                String current = assay.getAvailableDetectionMethods().get(i);
                templateList.addItem(current);
                if (current.equals(assay.getSelectedDetectionMethod()))
                    selectedIndex = i;
            }
            if (selectedIndex >= 0)
                templateList.setSelectedIndex(selectedIndex);
            if (templateList.getItemCount() > 0)
                assay.setSelectedDetectionMethod(templateList.getValue(templateList.getSelectedIndex()));
            templateList.addChangeHandler(new ChangeHandler()
            {
                public void onChange(ChangeEvent event)
                {
                    assay.setSelectedDetectionMethod(templateList.getValue(templateList.getSelectedIndex()));
                    setDirty(true);
                }
            });

            DOM.setElementAttribute(templateList.getElement(), "id", "detectionMethod");
            HorizontalPanel picker = new HorizontalPanel();
            picker.add(templateList);
            picker.setVerticalAlignment(ALIGN_BOTTOM);

            table.getFlexCellFormatter().setStyleName(row, 0, "labkey-form-label");
            table.setWidget(row++, 1, picker);
        }

        if (!assay.getAvailableMetadataInputFormats().isEmpty())
        {
            // file based metadata
            FlowPanel metadataInputPanel = new FlowPanel();
            metadataInputPanel.add(new InlineHTML("Metadata Input Format"));
            metadataInputPanel.add(new HelpPopup("Metadata Input Format", assay.getMetadataInputFormatHelp()));

            table.setWidget(row, 0, metadataInputPanel);
            final ListBox metadataSelection = new ListBox();
            String selectedFormat = assay.getSelectedMetadataInputFormat();
            int selectedIndex = -1;
            int i=0;
            for (Map.Entry<String, String> entry : assay.getAvailableMetadataInputFormats().entrySet())
            {
                metadataSelection.addItem(entry.getValue(), entry.getKey());
                if (entry.getKey().equals(selectedFormat))
                    selectedIndex = i;

                i++;
            }
            metadataSelection.setSelectedIndex(selectedIndex);
            metadataSelection.addChangeHandler(new ChangeHandler()
            {
                public void onChange(ChangeEvent event)
                {
                    assay.setSelectedMetadataInputFormat(metadataSelection.getValue(metadataSelection.getSelectedIndex()));
                    setDirty(true);
                }
            });

            DOM.setElementAttribute(metadataSelection.getElement(), "id", "metadataInputFormat");

            HorizontalPanel metadata = new HorizontalPanel();
            metadata.add(metadataSelection);
            metadata.setVerticalAlignment(ALIGN_BOTTOM);
            table.getFlexCellFormatter().setStyleName(row, 0, "labkey-form-label");
            table.setWidget(row++, 1, metadata);
        }

        if (assay.isAllowTransformationScript())
        {
            _transformScriptTable = new FlexTable();
            final FlexTableRowDragController tableRowDragController = new FlexTableRowDragController(_rootPanel)
            {
                @Override
                public void dragEnd()
                {
                    super.dragEnd();
                    // Update the object with the new ordering
                    if (_assay != null)
                    {
                        syncTransformScripts(_assay);
                    }
                }
            };
            
            for (String transformScriptPath : assay.getProtocolTransformScripts())
            {
                addTransformScriptToTable(assay, transformScriptPath, tableRowDragController);
            }

            FlowPanel transformNamePanel = new FlowPanel();
            transformNamePanel.add(new InlineHTML("Transform Scripts"));
            transformNamePanel.add(new HelpPopup("Transform Scripts", "<div>The full path to the transform script file. " +
                    "Transform scripts run before the assay data is imported and can reshape the data file to match " +
                    "the expected import format. " +
                    "For help writing a transform script refer to the " +
                    "<a href=\"https://www.labkey.org/wiki/home/Documentation/page.view?name=programmaticQC\" target=\"_blank\">Programmatic Quality Control & Transformations</a> guide.</div>" +
                    "<br><div>The extension of the script file " +
                    "identifies the script engine that will be used to run the validation script. For example, " +
                    "a script named test.pl will be run with the Perl scripting engine. The scripting engine must be " +
                    "configured on the Views and Scripting page in the Admin Console. For additional information refer to " +
                    "the <a href=\"https://www.labkey.org/wiki/home/Documentation/page.view?name=configureScripting\" target=\"_blank\">help documentation</a>.</div>"));
            table.getFlexCellFormatter().setStyleName(row, 0, "labkey-form-label");
            table.setWidget(row, 0, transformNamePanel);

            VerticalPanel transformPanel = new VerticalPanel();
            transformPanel.add(_transformScriptTable);

            FlowPanel buttonPanel = new FlowPanel();
            if (assay.isAllowTransformationScript())
            {
                ImageButton addScriptButton = new ImageButton("Add Script");
                addScriptButton.addClickHandler(new ClickHandler()
                {
                    public void onClick(ClickEvent event)
                    {
                        addTransformScriptToTable(assay, null, tableRowDragController);
                    }
                });
                buttonPanel.add(addScriptButton);
            }
            // add a download sample data button if the protocol already exists
            if (_protocolId != null)
            {
                String url = PropertyUtil.getRelativeURL("downloadSampleQCData", "assay");
                url += "?rowId=" + _protocolId;

                buttonPanel.add(new LinkButton("Download Test Data", url));
            }
            if (buttonPanel.getWidgetCount() > 0)
            {
                transformPanel.add(buttonPanel);
            }

            table.setWidget(row++, 1, transformPanel);

            FlexTableRowDropController controller = new FlexTableRowDropController(_transformScriptTable)
            {
                @Override
                protected void handleDrop(FlexTable sourceTable, FlexTable targetTable, int sourceRow, int targetRow)
                {
                    // If we're dragging from the top of the table to the bottom, shift everything between up one row
                    for (int i = sourceRow; i < targetRow; i++)
                    {
                        BoundTextBox box1 = getTransformScriptTextBox(i);
                        BoundTextBox box2 = getTransformScriptTextBox(i + 1);

                        _transformScriptTable.setWidget(i + 1, TRANSFORM_SCRIPT_PATH_COLUMN_INDEX, box1);
                        _transformScriptTable.setWidget(i, TRANSFORM_SCRIPT_PATH_COLUMN_INDEX, box2);
                    }

                    // If we're dragging from the bottom of the table to the top, shift everything between down one row
                    for (int i = sourceRow; i > targetRow && i > 0; i--)
                    {
                        BoundTextBox box1 = getTransformScriptTextBox(i);
                        BoundTextBox box2 = getTransformScriptTextBox(i - 1);

                        _transformScriptTable.setWidget(i - 1, TRANSFORM_SCRIPT_PATH_COLUMN_INDEX, box1);
                        _transformScriptTable.setWidget(i, TRANSFORM_SCRIPT_PATH_COLUMN_INDEX, box2);
                    }

                    setDirty(true);
                }
            };
            tableRowDragController.registerDropController(controller);

            // validation scripts defined at the type or global level are read only
            if (!assay.getModuleTransformScripts().isEmpty())
            {
                FlowPanel validationPanel = new FlowPanel();
                validationPanel.add(new InlineLabel("Module-Provided Scripts"));
                validationPanel.add(new HelpPopup("Module-Provided Scripts", "<div>The full path to the script files. " +
                        "These scripts are part of the assay type and cannot be removed. They will run after any custom scripts configured above.</div>" +
                        "<br><div>The extension of the script file " +
                        "identifies the script engine that will be used to run the validation script. For example, " +
                        "a script named test.pl will be run with the Perl scripting engine. The scripting engine must be " +
                        "configured on the Views and Scripting page in the Admin Console. For additional information refer to " +
                        "the <a href=\"https://www.labkey.org/wiki/home/Documentation/page.view?name=configureScripting\" target=\"_blank\">help documentation</a>.</div>"));
                table.setWidget(row, 0, validationPanel);
                table.getFlexCellFormatter().setStyleName(row, 0, "labkey-form-label");

                VerticalPanel moduleScriptPanel = new VerticalPanel();
                for (String path : assay.getModuleTransformScripts())
                {
                    moduleScriptPanel.add(new Label(path));
                }

                table.setWidget(row++, 1, moduleScriptPanel);

            }

            // add a checkbox to enter debug mode
            _debugScriptFiles.setBool(assay.isSaveScriptFiles());
            BoundCheckBox debugScriptFilesCheckBox = new BoundCheckBox("id_debug_script", "debugScript", _debugScriptFiles, this);
            FlowPanel debugPanel = new FlowPanel();
            debugPanel.add(new InlineHTML("Save Script Data"));
            debugPanel.add(new HelpPopup("Save Script Data", "Typically transform and validation script data files are deleted on script completion. " +
                    "For debug purposes, it can be helpful to be able to view the files generated by the server that are passed to the script. " +
                    "If this checkbox is checked, files will be saved to a subfolder named: \"TransformAndValidationFiles\", located in the same folder " +
                    "that the original script is located."));
            table.setWidget(row, 0, debugPanel);
            table.getFlexCellFormatter().setStyleName(row, 0, "labkey-form-label");
            table.setWidget(row++, 1, debugScriptFilesCheckBox);
        }

        _editableRuns.setBool(assay.isEditableRuns());
        FlowPanel editableRunPanel = new FlowPanel();
        editableRunPanel.add(new InlineHTML("Editable Runs"));
        editableRunPanel.add(new HelpPopup("Editable Runs", "If enabled, users with sufficient permissions can " +
                "edit values at the run level after the initial import is complete. " +
                "These changes will be audited."));
        table.setWidget(row, 0, editableRunPanel);
        table.getFlexCellFormatter().setStyleName(row, 0, "labkey-form-label");
        table.setWidget(row++, 1, new BoundCheckBox("id_editable_run_properties", "editableRunProperties", _editableRuns, this));

        _editableResults.setBool(assay.isEditableResults());
        if ("true".equals(PropertyUtil.getServerProperty("supportsEditableResults")))
        {
            FlowPanel editableResultsPanel = new FlowPanel();
            editableResultsPanel.add(new InlineHTML("Editable Results"));
            editableResultsPanel.add(new HelpPopup("Editable Runs", "If enabled, users with sufficient permissions can " +
                    "edit and delete at the individual results row level after the initial import is complete. " +
                    "These changes will be audited. New result rows cannot be added to existing runs."));
            table.setWidget(row, 0, editableResultsPanel);
            table.getFlexCellFormatter().setStyleName(row, 0, "labkey-form-label");
            table.setWidget(row++, 1, new BoundCheckBox("id_editable_results_properties", "editableResultProperties", _editableResults, this));
        }

        _backgroundUpload.setBool(assay.isBackgroundUpload());
        if ("true".equals(PropertyUtil.getServerProperty("supportsBackgroundUpload")))
        {
            FlowPanel backgroundUploadPanel = new FlowPanel();
            backgroundUploadPanel.add(new InlineHTML("Import in Background"));
            backgroundUploadPanel.add(new HelpPopup("Import in Background", "If enabled, assay imports will be processed as jobs in the data pipeline. " +
                    "If there are any errors during the import, they can be viewed from the log file for that job."));
            table.setWidget(row, 0, backgroundUploadPanel);
            table.getFlexCellFormatter().setStyleName(row, 0, "labkey-form-label");
            table.setWidget(row++, 1, new BoundCheckBox("id_background_upload_properties", "backgroundUpload", _backgroundUpload, this));
        }

        return table;
    }

    private void addTransformScriptToTable(final GWTProtocol assay, String transformScriptPath, final FlexTableRowDragController tableRowDragController)
    {
        int row = _transformScriptTable.getRowCount();
        BoundTextBox textBox = new ValidatorTextBox("Transform Script", "AssayDesignerTransformScript" + row, transformScriptPath, new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                syncTransformScripts(assay);
            }
        }, this, _debugScriptFiles, _allowSpacesInPath);
        textBox.getBox().setVisibleLength(79);

        final FontButton deleteButton = PropertiesEditor.getDeleteButton("removeTransformScript" + row, null);
        deleteButton.setTitle("Click to remove");
        deleteButton.addClickHandler(new ClickHandler()
        {
            public void onClick(ClickEvent event)
            {
                for (int i = 0; i < _transformScriptTable.getRowCount(); i++)
                {
                    if (_transformScriptTable.getWidget(i, TRANSFORM_SCRIPT_REMOVE_BUTTON_COLUMN_INDEX) == deleteButton)
                    {
                        _transformScriptTable.removeRow(i);
                        syncTransformScripts(assay);

                        setRemoveButtonVisibility(_transformScriptTable, tableRowDragController);
                    }
                }
            }
        });
        _transformScriptTable.setWidget(row, TRANSFORM_SCRIPT_PATH_COLUMN_INDEX, textBox);
        Image dragIcon = new Image(PropertyUtil.getContextPath() + "/_images/partupdown.png");
        dragIcon.setTitle("Drag and drop to reorder");
        _transformScriptTable.setWidget(row, TRANSFORM_SCRIPT_DRAG_LABEL_COLUMN_INDEX, dragIcon);
        _transformScriptTable.setWidget(row, TRANSFORM_SCRIPT_REMOVE_BUTTON_COLUMN_INDEX, deleteButton);

        setRemoveButtonVisibility(_transformScriptTable, tableRowDragController);
    }

    private void setRemoveButtonVisibility(FlexTable scriptTable, FlexTableRowDragController tableRowDragController)
    {
        boolean multiple = scriptTable.getRowCount() > 1;
        for (int row = 0; row < scriptTable.getRowCount(); row++)
        {
            scriptTable.getWidget(row, TRANSFORM_SCRIPT_DRAG_LABEL_COLUMN_INDEX).setVisible(multiple);
            if (multiple)
            {
                tableRowDragController.makeDraggable(scriptTable.getWidget(row, TRANSFORM_SCRIPT_DRAG_LABEL_COLUMN_INDEX));
            }
            else
            {
                tableRowDragController.makeDraggable(scriptTable.getWidget(row, TRANSFORM_SCRIPT_DRAG_LABEL_COLUMN_INDEX));
            }
        }
    }

    private void syncTransformScripts(GWTProtocol assay)
    {
        List<String> transformScriptPaths = new ArrayList<String>();
        for (int row = 0; row < _transformScriptTable.getRowCount(); row++)
        {
            BoundTextBox textBox = getTransformScriptTextBox(row);
            String path = StringUtils.trimToNull(textBox.getBox().getText());
            if (path != null)
            {
                transformScriptPaths.add(path);
            }
        }
        if (!transformScriptPaths.equals(assay.getProtocolTransformScripts()))
        {
            setDirty(true);
            assay.setProtocolTransformScripts(transformScriptPaths);
        }
    }

    private boolean hasAutoCopyTargets()
    {
        return _autoCopyTargets != null && !_autoCopyTargets.isEmpty();
    }

    private boolean validate()
    {
        List<String> errors = new ArrayList<String>();
        String error = _nameBox.validate();
        if (error != null)
            errors.add(error);

        int numProps = 0;

        // Get the errors for each of the PropertiesEditors
        for (PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor> propeditor : _domainEditors)
        {
            List<String> domainErrors = propeditor.validate();
            if (domainErrors.size() > 0)
                errors.addAll(domainErrors);
        }

        if (_isPlateBased && _assay.getSelectedPlateTemplate() == null)
            errors.add("You must select a plate template from the list, or create one first.");

        if (_transformScriptTable != null)
        {
            for (int row = 0; row < _transformScriptTable.getRowCount(); row++)
            {
                BoundTextBox boundTextBox = getTransformScriptTextBox(row);
                if (!boundTextBox.checkValid())
                {
                    errors.add(boundTextBox.validate());
                }
            }
        }

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
            return true;
    }

    private BoundTextBox getTransformScriptTextBox(int row)
    {
        return (BoundTextBox) _transformScriptTable.getWidget(row, TRANSFORM_SCRIPT_PATH_COLUMN_INDEX);
    }

    public void save()
    {
        save(null);
    }

    public void save(final SaveListener<GWTProtocol> listener)
    {
        saveAsync(new ErrorDialogAsyncCallback<GWTProtocol>("Save failed")
        {
            @Override
            protected void handleFailure(String message, Throwable caught)
            {
                _saveInProgress = false;
                setDirty(true);
            }

            public void onSuccess(GWTProtocol result)
            {
                _saveInProgress = false;
                setDirty(false);
                _statusLabel.setHTML(STATUS_SUCCESSFUL);
                _assay = result;
                _copy = false;
                show(_assay);

                if (_designerURL.indexOf("?") == -1)
                {
                    _designerURL = _designerURL + "?";
                }
                if (_designerURL.indexOf("&rowId=") == -1)
                {
                    _designerURL = _designerURL + "&rowId=" + result.getProtocolId();
                }

                // issue 14853 : if we are coming from the copy assay page, remove the copy param and old assay design rowId
                if (_designerURL.indexOf("&copy=true") > -1)
                {
                    _designerURL = _designerURL.substring(0, _designerURL.indexOf("?")+1);
                    _designerURL = _designerURL + "rowId=" + result.getProtocolId();
                    _designerURL = _designerURL + "&providerName=" + result.getProviderName();
                }

                if (listener != null)
                {
                    listener.saveSuccessful(result, _designerURL);
                }
            }
        });
        setAllowSave(false);
    }

    private void saveAsync(AsyncCallback<GWTProtocol> callback)
    {
        if (validate())
        {
            List<GWTDomain<GWTPropertyDescriptor>> domains = new ArrayList<GWTDomain<GWTPropertyDescriptor>>();

            for (PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor> domainEditor : _domainEditors)
            {
                domains.add(domainEditor.getUpdates());
            }
            _assay.setDomains(domains);
            if (_autoCopyCheckBox.getValue().booleanValue() && _autoCopyTargetListBox.getSelectedIndex() > 0)
            {
                // One extra for the blank row
                assert _autoCopyTargetListBox.getItemCount() == _autoCopyTargets.size() + 1;
                _assay.setAutoCopyTargetContainer(_autoCopyTargets.get(_autoCopyTargetListBox.getSelectedIndex() - 1));
            }
            else
            {
                _assay.setAutoCopyTargetContainer(null);
            }

            _assay.setProviderName(_providerName);
            _assay.setSaveScriptFiles(_debugScriptFiles.booleanValue());
            _assay.setEditableRuns(_editableRuns.booleanValue());
            _assay.setEditableResults(_editableResults.booleanValue());
            _assay.setBackgroundUpload(_backgroundUpload.booleanValue());
            _saveInProgress = true;
            getService().saveChanges(_assay, true, callback);
        }
    }

    public void cancel()
    {
        // if the user has canceled, we don't need to run the dirty page checking
        if (_closeHandlerManager != null)
        {
            _closeHandlerManager.removeHandler();
            _closeHandlerManager = null;
        }
        String url = _returnURL;
        if (url == null)
        {
            url = PropertyUtil.getContextPath() + "/Project" + PropertyUtil.getContainerPath() + "/begin.view";
        }
        WindowUtil.setLocation(url);
    }

    public void finish()
    {
        // If a new assay is never saved, there are no details to view, so it will take the user to the study
        final String doneLink;
        if (_returnURL != null)
            doneLink = _returnURL;
        else if (_assay != null && _assay.getProtocolId() != null)
            doneLink = PropertyUtil.getContextPath() + "/assay" + PropertyUtil.getContainerPath() + "/assayRuns.view?rowId=" + _assay.getProtocolId();
        else
            doneLink = PropertyUtil.getContextPath() + "/Project" + PropertyUtil.getContainerPath() + "/begin.view";

        if (!_dirty)
        {
            // No need to save
            WindowUtil.setLocation(doneLink);
        }
        else
        {
            saveAsync(new ErrorDialogAsyncCallback<GWTProtocol>("Save failed")
            {
                @Override
                protected void handleFailure(String message, Throwable caught)
                {
                    _saveInProgress = false;
                    setDirty(true);
                }
                
                public void onSuccess(GWTProtocol protocol)
                {
                    if (_closeHandlerManager != null)
                    {
                        _closeHandlerManager.removeHandler();
                        _closeHandlerManager = null;
                    }
                    WindowUtil.setLocation(doneLink);
                }
            });
        }
    }

    class ValidatorTextBox extends BoundTextBox
    {
        private BooleanProperty _debugMode;
        private boolean _allowSpacesInPath;

        public ValidatorTextBox(String caption, String id, String initialValue, WidgetUpdatable updatable,
                                DirtyCallback dirtyCallback, BooleanProperty debugMode, boolean allowSpacesInPath)
        {
            super(caption, id, initialValue, updatable, dirtyCallback);
            _debugMode = debugMode;
            _allowSpacesInPath = allowSpacesInPath;
        }

        @Override
        protected String validateValue(String text)
        {
            if (!_allowSpacesInPath && _debugMode.booleanValue())
            {
                if (text.contains(" "))
                    return _caption + ": The path to the script should not contain spaces when the Save Script Data check box is selected.";
            }
            return super.validateValue(text);
        }
    }
}
