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

package org.labkey.api.gwt.client.assay;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.DOM;
import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.URL;
import org.labkey.api.gwt.client.model.GWTContainer;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.ServiceUtil;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.gwt.client.ui.*;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;

import java.util.*;

/**
 * User: brittp
 * Date: Jun 20, 2007
 * Time: 2:24:04 PM
 */
public class AssayDesignerMainPanel extends VerticalPanel implements Saveable<GWTProtocol>, DirtyCallback
{
    private RootPanel _rootPanel;
    private AssayServiceAsync _testService;
    private final String _providerName;
    private Integer _protocolId;
    protected GWTProtocol _assay;
    private boolean _dirty;
    private List<PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor>> _domainEditors = new ArrayList<PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor>>();
    private HTML _statusLabel = new HTML("<br/>");
    private static final String STATUS_SUCCESSFUL = "Save successful.<br/>";
    private BoundTextBox _nameBox;
    private CheckBox _autoCopyCheckBox;
    private ListBox _autoCopyTargetListBox;
    private boolean _copy;
    private final String _returnURL;
    private SaveButtonBar saveBarTop;
    private SaveButtonBar saveBarBottom;
    private HandlerRegistration _closeHandlerManager;
    private List<GWTContainer> _autoCopyTargets;
    private boolean _isPlateBased;

    public AssayDesignerMainPanel(RootPanel rootPanel)
    {
        _rootPanel = rootPanel;

        String protocolIdStr = PropertyUtil.getServerProperty("protocolId");
        _protocolId = protocolIdStr != null ? new Integer(Integer.parseInt(protocolIdStr)) : null;
        _providerName = PropertyUtil.getServerProperty("providerName");
        _returnURL = PropertyUtil.getServerProperty("returnURL");
        String copyStr = PropertyUtil.getServerProperty("copy");
        _copy = copyStr != null && Boolean.TRUE.toString().equals(copyStr);

        _autoCopyTargetListBox = new ListBox();
        _autoCopyTargetListBox.setEnabled(false);

        _autoCopyCheckBox = new CheckBox(" Automatically copy uploaded data to study: ");
        _autoCopyCheckBox.setEnabled(false);
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
            _testService = (AssayServiceAsync) GWT.create(AssayService.class);
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
        WebPartPanel infoPanel = new WebPartPanel("Assay Properties", table);
        infoPanel.setWidth("100%");
        _rootPanel.add(infoPanel);

        for (int i = 0; i < _assay.getDomains().size(); i++)
        {
            _rootPanel.add(new HTML("<br/>"));

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

        _rootPanel.add(new HTML("<br/>"));
        saveBarBottom = new SaveButtonBar(this);
        _rootPanel.add(saveBarBottom);

        // When first creating an assay, the name will be empty. If that's the case, we need to
        // set the dirty bit
        if ("".equals(_nameBox.getBox().getText()))
            setDirty(true);
        else
            setDirty(_copy);

        _closeHandlerManager = Window.addWindowClosingHandler(new AssayCloseListener());

        syncAutoCopy();
    }

    protected void addErrorMessage(String message)
    {
        VerticalPanel mainPanel = new VerticalPanel();
        mainPanel.add(new Label(message));
        _rootPanel.add(mainPanel);
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
        if (assay.getProtocolId() == null || _copy)
        {
            table.setWidget(row, 1, _nameBox);
            table.setHTML(row++, 0, "Name (Required)");
        }
        else
        {
            table.setHTML(row, 1, assayName);
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
        table.setWidget(row++, 1, descriptionBox);

        HorizontalPanel autoCopyPanel = new HorizontalPanel();
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
        autoCopyPanel.add(_autoCopyCheckBox);
        autoCopyPanel.add(_autoCopyTargetListBox);

        table.setWidget(row++, 1, autoCopyPanel);

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
            picker.add(new HTML("&nbsp;[<a href=\"" + PropertyUtil.getRelativeURL("plateTemplateList", "Plate") + "\">configure templates</a>]"));
            picker.setVerticalAlignment(ALIGN_BOTTOM);
            table.setWidget(row++, 1, picker);
        }

        if (assay.isAllowValidationScript())
        {
            table.setWidget(row++, 0, new HTML("&nbsp;"));

            HTML title = new HTML("Validation and Transformation Scripts");
            title.setStyleName("labkey-wp-title");
            table.setWidget(row++, 1, title);

            if (assay.isAllowTransformationScript())
            {
                BoundTextBox transformFile = new BoundTextBox("Data Transform", "AssayDesignerTransformScript", assay.getProtocolTransformScript(), new WidgetUpdatable()
                {
                    public void update(Widget widget)
                    {
                        if (!((TextBox) widget).getText().equals(StringUtils.trimToEmpty(assay.getProtocolTransformScript())))
                        {
                            setDirty(true);
                            assay.setProtocolTransformScript(StringUtils.trimToEmpty(((TextBox)widget).getText()));
                        }
                    }
                }, this);
                transformFile.getBox().setVisibleLength(79);
                FlowPanel transformNamePanel = new FlowPanel();
                transformNamePanel.add(new InlineHTML("Data Transform"));
                transformNamePanel.add(new HelpPopup("Data Transform", "The full path to the data transform script file. The extension of the script file " +
                        "identifies the script engine that will be used to run the transform script. For example: a script named test.pl will " +
                        "be run with the Perl scripting engine. The scripting engine must be configured from the Admin panel. For additional information " +
                        "refer to the <a href=\"https://www.labkey.org/wiki/home/Documentation/page.view?name=configureScripting\" target=\"_blank\">help documentation</a>."));
                table.setWidget(row, 0, transformNamePanel);
                table.setWidget(row++, 1, transformFile);
            }

            // validation scripts defined at the type or global level are read only
            for (String path : assay.getValidationScripts())
            {
                TextBox text = new TextBox();
                text.setText(path);
                text.setReadOnly(true);
                text.setVisibleLength(79);

                FlowPanel namePanel = new FlowPanel();
                namePanel.add(new InlineLabel("QC Validation"));
                namePanel.add(new HelpPopup("QC Validation", "Validation scripts can be assigned by default by the assay type. Default scripts cannot be " +
                        "removed from this view."));

                table.setWidget(row, 0, namePanel);
                table.setWidget(row++, 1, text);
            }

            BoundTextBox scriptFile = new BoundTextBox("QC Validation", "AssayDesignerQCScript", assay.getProtocolValidationScript(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    if (!((TextBox) widget).getText().equals(StringUtils.trimToEmpty(assay.getProtocolValidationScript())))
                    {
                        setDirty(true);
                        assay.setProtocolValidationScript(StringUtils.trimToEmpty(((TextBox)widget).getText()));
                    }
                }
            }, this);
            scriptFile.getBox().setVisibleLength(79);

            FlowPanel namePanel = new FlowPanel();
            namePanel.add(new InlineHTML("QC Validation"));
            namePanel.add(new HelpPopup("QC Validation", "The full path to the validation script file. The extension of the script file " +
                    "identifies the script engine that will be used to run the validation script. For example: a script named test.pl will " +
                    "be run with the Perl scripting engine. The scripting engine must be configured from the Admin panel. For additional information " +
                    "refer to the <a href=\"https://www.labkey.org/wiki/home/Documentation/page.view?name=configureScripting\" target=\"_blank\">help documentation</a>."));
            table.setWidget(row, 0, namePanel);
            table.setWidget(row, 1, scriptFile);

            // add a download sample data button if the protocol already exists
            if (_protocolId != null)
            {
                String url = PropertyUtil.getRelativeURL("downloadSampleQCData", "assay");
                url += "?rowId=" + _protocolId;

                LinkButton download = new LinkButton("Download Test Data", url);
                table.setWidget(row, 2, download);
            }
        }
        return table;
    }

    private boolean hasAutoCopyTargets()
    {
        return _autoCopyTargets != null && !_autoCopyTargets.isEmpty();
    }

    public void setDirty(boolean dirty)
    {
        if (dirty && _statusLabel.getText().equalsIgnoreCase(STATUS_SUCCESSFUL))
            _statusLabel.setHTML("<br/>");

        setAllowSave(dirty);

        _dirty = dirty;
    }

    public boolean isDirty()
    {
        return _dirty;
    }

    private void setAllowSave(boolean dirty)
    {
        if (saveBarTop != null)
            saveBarTop.setAllowSave(dirty);

        if (saveBarBottom != null)
            saveBarBottom.setAllowSave(dirty);
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
            numProps += propeditor.getPropertyCount(false);
            if (domainErrors.size() > 0)
                errors.addAll(domainErrors);
        }

        if (0 == numProps)
            errors.add("You must create at least one Property.");

        if (_isPlateBased && _assay.getSelectedPlateTemplate() == null)
            errors.add("You must select a plate template from the list, or create one first.");
        
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
                setDirty(true);
            }

            public void onSuccess(GWTProtocol result)
            {
                setDirty(false);
                _statusLabel.setHTML(STATUS_SUCCESSFUL);
                _assay = result;
                _copy = false;
                show(_assay);
                if (listener != null)
                {
                    String designerURL = PropertyUtil.getRelativeURL("designer", "assay");
                    designerURL += "?providerName=" + URL.encodeComponent(_providerName);
                    designerURL += "&rowId=" + result.getProtocolId();
                    listener.saveSuccessful(result, designerURL);
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
            getService().saveChanges(_assay, true, callback);
        }
    }

    public void cancel()
    {
        // We're already listening for navigation if the dirty bit is set,
        // so no extra handling is needed.
        String loc = PropertyUtil.getContextPath() + "/Project" + PropertyUtil.getContainerPath() + "/begin.view";
        WindowUtil.setLocation(loc);
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

    class AssayCloseListener implements Window.ClosingHandler
    {
        public void onWindowClosing(Window.ClosingEvent event)
        {
            boolean dirty = _dirty;
            for (int i = 0; i < _domainEditors.size() && !dirty; i++)
            {
                dirty = _domainEditors.get(i).isDirty();
            }
            if (dirty)
                event.setMessage("Changes have not been saved and will be discarded.");
        }
    }
}
