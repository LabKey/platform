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

package org.labkey.api.gwt.client.assay;

import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.WindowCloseListener;
import com.google.gwt.core.client.GWT;
import org.labkey.api.gwt.client.util.ServiceUtil;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.ui.*;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;

import java.util.*;

/**
 * User: brittp
 * Date: Jun 20, 2007
 * Time: 2:24:04 PM
 */
public class AssayDesignerMainPanel extends VerticalPanel implements Saveable
{
    private RootPanel _rootPanel;
    private AssayServiceAsync _testService;
    private final String _providerName;
    private Integer _protocolId;
    private GWTProtocol _assay;
    private boolean _dirty;
    private List _domainEditors = new ArrayList();
    private HTML _statusLabel = new HTML("<br/>");
    private String _statusSuccessful = "Save successful.";
    private BoundTextBox _nameBox;
    private boolean _copy;
    private SaveButtonBar saveBarTop;
    private SaveButtonBar saveBarBottom;


    public AssayDesignerMainPanel(RootPanel rootPanel, String providerName, Integer protocolId, boolean copy)
    {
        _providerName = providerName;
        _protocolId = protocolId;
        _rootPanel = rootPanel;
        _copy = copy;
    }

    public void showAsync()
    {
        _rootPanel.clear();
        _rootPanel.add(new Label("Loading..."));
        if (_protocolId != null)
        {
            getService().getAssayDefinition(_protocolId.intValue(), _copy, new AsyncCallback()
            {
                public void onFailure(Throwable throwable)
                {
                    VerticalPanel mainPanel = new VerticalPanel();
                    mainPanel.add(new Label("Unable to load assay definition: " + throwable.getMessage()));
                    _rootPanel.add(mainPanel);
                }

                public void onSuccess(Object object)
                {
                    show((GWTProtocol) object);
                }
            });
        }
        else
        {
            getService().getAssayTemplate(_providerName, new AsyncCallback()
            {
                public void onFailure(Throwable throwable)
                {
                    VerticalPanel mainPanel = new VerticalPanel();
                    mainPanel.add(new Label("Unable to load assay template: " + throwable.getMessage()));
                    _rootPanel.add(mainPanel);
                }

                public void onSuccess(Object object)
                {
                    show((GWTProtocol) object);
                }
            });
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

    private interface WidgetUpdatable
    {
        void update(Widget widget);
    }

    private String safeStr(String str)
    {
        return str != null ? str : "";
    }

    private class BoundTextBox extends HorizontalPanel
    {
        protected TextBox _box;
        protected String _caption;
        public BoundTextBox(String caption, String initialValue, final WidgetUpdatable updatable)
        {
            _caption = caption;
            _box = new TextBox();
            _box.setText(safeStr(initialValue));
            _box.addFocusListener(new FocusListenerAdapter()
            {
                public void onLostFocus(Widget sender)
                {
                    updatable.update(sender);
                }
            });
            _box.addChangeListener(new ChangeListener()
            {
                public void onChange(Widget sender)
                {
                    updatable.update(sender);
                }
            });
            _box.addKeyboardListener(new KeyboardListenerAdapter()
            {
                public void onKeyPress(Widget sender, char keyCode, int modifiers)
                {
                    setDirty(true);
                }
            });
            add(_box);
        }

        public String validate()
        {
            if (_box.getText() == null || _box.getText().length() == 0)
                return "\"" + _caption + "\" is required.";
            return null;
        }
    }
    // Max Samples is not being used in the 2.2 release
//    private class BoundIntegerTextBox extends BoundTextBox
//    {
//        public BoundIntegerTextBox(String caption, int initialValue, WidgetUpdatable updatable)
//        {
//            super(caption, "" + initialValue, updatable);
//        }
//
//        public String validate()
//        {
//            String errors = super.validate();
//            if (errors == null)
//            {
//                String countStr = _box.getText();
//                try
//                {
//                    int count = Integer.parseInt(countStr);
//                    if (count < 1)
//                        errors = "The value for \"" + _caption + "\" must be greater than zero";
//                }
//                catch (NumberFormatException e)
//                {
//                    errors = "The value \"" + countStr + "\" is not a valid integer value for \"" + _caption + "\".";
//                }
//            }
//            return errors;
//        }
//    }

    private class BoundTextAreaBox extends HorizontalPanel
    {
        protected TextArea _box;
        protected String _caption;
        public BoundTextAreaBox(String caption, String initialValue, final WidgetUpdatable updatable)
        {
            _caption = caption;
            _box = new TextArea();
            _box.setText(safeStr(initialValue));
            _box.setWidth("350px");
            _box.addFocusListener(new FocusListenerAdapter()
            {
                public void onLostFocus(Widget sender)
                {
                    updatable.update(sender);
                }
            });
            _box.addChangeListener(new ChangeListener()
            {
                public void onChange(Widget sender)
                {
                    updatable.update(sender);
                }
            });
            _box.addKeyboardListener(new KeyboardListenerAdapter()
            {
                public void onKeyPress(Widget sender, char keyCode, int modifiers)
                {
                    setDirty(true);
                }
            });
            add(_box);
        }

        public String validate()
        {
            if (_box.getText() == null || _box.getText().length() == 0)
                return "\"" + _caption + "\" is required.";
            return null;
        }
    }

    private void show(GWTProtocol assay)
    {
        _assay = assay;

        _rootPanel.clear();
        _domainEditors.clear();
        saveBarTop = new SaveButtonBar(this);
        _rootPanel.add(saveBarTop);
        _rootPanel.add(_statusLabel);

        Widget infoPanel = createAssayInfo(_assay);
        _rootPanel.add(infoPanel);

        for (int i = 0; i < _assay.getDomains().size(); i++)
        {
            _rootPanel.add(new HTML("<br/>"));

            GWTDomain domain = (GWTDomain) _assay.getDomains().get(i);

            PropertiesEditor editor = createPropertiesEditor(domain);
            editor.addChangeListener(new ChangeListener()
            {
                public void onChange(Widget sender)
                {
                    setDirty(true);
                }
            });

            editor.init(domain);
            _domainEditors.add(editor);
            editor.setMode(PropertiesEditor.modeEdit);

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

        _rootPanel.add(new HTML("<br/><br/>"));
        saveBarBottom = new SaveButtonBar(this);
        _rootPanel.add(saveBarBottom);
        setDirty(_copy);
        
        Window.addWindowCloseListener(new WindowCloseListener()
        {
            public void onWindowClosed()
            {
            }

            public String onWindowClosing()
            {
                boolean dirty = _dirty;
                for (int i = 0; i < _domainEditors.size() && !dirty; i++)
                {
                    dirty = ((PropertiesEditor)_domainEditors.get(i)).isDirty();
                }
                if (dirty)
                    return "Changes have not been saved and will be discarded.";
                else
                    return null;
            }
        });
    }

    protected PropertiesEditor createPropertiesEditor(GWTDomain domain)
    {
        return new PropertiesEditor(getLookupService());
    }

    private Widget createAssayInfo(final GWTProtocol assay)
    {
        final FlexTable table = new FlexTable();
        final String assayName = assay.getProtocolId() != null ? assay.getName() : null;
        _nameBox = new BoundTextBox("Name", assayName, new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                assay.setName(((TextBox) widget).getText());
                if (!((TextBox) widget).getText().equals(assayName))
                {
                    setDirty(true);
                }
            }
        });

        if (assay.getProtocolId() == null || _copy)
        {
            table.setWidget(0, 1, _nameBox);
            table.setHTML(0, 0, "Name (Required)");
        }
        else
        {
            table.setHTML(0, 1, assayName);
            table.setHTML(0, 0, "Name");
        }

        BoundTextAreaBox descriptionBox = new BoundTextAreaBox("Description", assay.getDescription(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                if (!((TextArea) widget).getText().equals(assay.getDescription()))
                {
                    setDirty(true);
                }
                assay.setDescription(((TextArea) widget).getText());
            }
        });
        table.setHTML(1, 0, "Description");
        table.setWidget(1, 1, descriptionBox);

        if (assay.getAvailablePlateTemplates() != null)
        {
            table.setHTML(2, 0, "Plate Template");
            final ListBox templateList = new ListBox();
            int selectedIndex = -1;
            for (int i = 0; i < assay.getAvailablePlateTemplates().size(); i++)
            {
                String current = (String) assay.getAvailablePlateTemplates().get(i);
                templateList.addItem(current);
                if (current.equals(assay.getSelectedPlateTemplate()))
                    selectedIndex = i;
            }
            if (selectedIndex >= 0)
                templateList.setSelectedIndex(selectedIndex);
            if (templateList.getItemCount() > 0)
                assay.setSelectedPlateTemplate(templateList.getValue(templateList.getSelectedIndex()));
            templateList.addChangeListener(new ChangeListener()
            {
                public void onChange(Widget sender)
                {
                    assay.setSelectedPlateTemplate(templateList.getValue(templateList.getSelectedIndex()));
                    setDirty(true);
                }
            });
            HorizontalPanel picker = new HorizontalPanel();
            picker.add(templateList);
            picker.add(new HTML("&nbsp;[<a href=\"" + PropertyUtil.getRelativeURL("plateTemplateList", "Plate") + "\">configure templates</a>]"));
            picker.setVerticalAlignment(ALIGN_BOTTOM);
            table.setWidget(2, 1, picker);
        }
        WebPartPanel panel = new WebPartPanel("Assay Properties", table);
        panel.setWidth("100%");
        return panel;
    }

    private void setDirty(boolean dirty)
    {
        if (dirty && _statusLabel.getText().equalsIgnoreCase(_statusSuccessful))
            _statusLabel.setHTML("<br/>");

        if (saveBarTop != null)
            saveBarTop.ownerDirty(dirty);

        if (saveBarBottom != null)
            saveBarBottom.ownerDirty(dirty);

        _dirty = dirty;
    }

    private boolean validate()
    {
        List errors = new ArrayList();
        String error = _nameBox.validate();
        if (error != null)
            errors.add(error);

        int numProps = 0;

        // Get the errors for each of the PropertiesEditors
        for (int i = 0; i < _domainEditors.size(); i++)
        {
            PropertiesEditor propeditor = (PropertiesEditor)(_domainEditors.get(i));
            List domainErrors = propeditor.validate();
            numProps += propeditor.getPropertyCount(false);
            if (domainErrors != null && domainErrors.size() > 0)
                errors.addAll(domainErrors);
        }

        if(0 == numProps)
            errors.add("You must create at least one Property.");

        if (errors.size() > 0)
        {
            String errorString = "";
            for (int i = 0; i < errors.size(); i++)
            {
                if (i > 0)
                    errorString += "\n";
                errorString += (String) errors.get(i);
            }
            Window.alert(errorString);
            return false;
        }
        else
            return true;
    }

    public void save()
    {
        save(new AsyncCallback()
        {
            public void onFailure(Throwable caught)
            {
                Window.alert(caught.getMessage());
            }

            public void onSuccess(Object result)
            {
                setDirty(false);
                _statusLabel.setHTML( _statusSuccessful);
                _assay = (GWTProtocol) result;
                _copy = false;
                show(_assay);
            }
        });
    }

    public void save(AsyncCallback callback)
    {
        if (validate())
        {
            List domains = new ArrayList();

            for (int i = 0; i < _domainEditors.size(); i++)
            {
                domains.add(((PropertiesEditor)_domainEditors.get(i)).getDomainUpdates());
            }
            _assay.setDomains(domains);

            _assay.setProviderName(_providerName);
            getService().saveChanges(_assay, true, callback);
        }
    }

    public void cancel()
    {
        // We're already listening for navigation if the dirty bit is set,
        // so no extra handling is needed.
        WindowUtil.back();
    }

    public void finish()
    {
        // If a new assay is never saved, there are no details to view, so it will take the user to the study
        final String doneLink;
        if (_assay != null && _assay.getProtocolId() != null)
            doneLink = PropertyUtil.getContextPath() + "/" + PropertyUtil.getPageFlow() + PropertyUtil.getContainerPath() + "/assayRuns.view?rowId=" + _assay.getProtocolId();
        else
            doneLink = PropertyUtil.getContextPath() + "/Project" + PropertyUtil.getContainerPath() + "/begin.view";

        if (!_dirty)
        {
            // No need to save
            WindowUtil.setLocation(doneLink);
        }
        else
        {
            save(new AsyncCallback()
            {
                public void onFailure(Throwable caught)
                {
                    Window.alert(caught.getMessage());
                }

                public void onSuccess(Object result)
                {
                    // save was successful, so don't prompt when navigating away
                    setDirty(false);

                    WindowUtil.setLocation(doneLink);
                }
            });
        }

    }

    protected LookupServiceAsync getLookupService()
    {
        return new LookupServiceAsync()
        {
            public void getContainers(AsyncCallback async)
            {
                getService().getContainers(async);
            }

            public void getSchemas(String containerId, AsyncCallback async)
            {
                getService().getSchemas(containerId, async);
            }

            public void getTablesForLookup(String containerId, String schemaName, AsyncCallback async)
            {
                getService().getTablesForLookup(containerId, schemaName, async);
            }
        };
    }
}
