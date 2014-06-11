/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

package gwt.client.org.labkey.specimen.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.BoundTextBox;
import org.labkey.api.gwt.client.ui.DirtyCallback;
import org.labkey.api.gwt.client.ui.PropertiesEditor;
import org.labkey.api.gwt.client.ui.SaveButtonBar;
import org.labkey.api.gwt.client.ui.Saveable;
import org.labkey.api.gwt.client.ui.WebPartPanel;
import org.labkey.api.gwt.client.ui.WidgetUpdatable;
import org.labkey.api.gwt.client.ui.WindowUtil;
import org.labkey.api.gwt.client.util.BooleanProperty;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SpecimenDesignerMainPanel extends VerticalPanel implements Saveable<List<String>>, DirtyCallback
{
    private RootPanel _rootPanel;
    private SpecimenServiceAsync _testService;

    private GWTDomain<GWTPropertyDescriptor> domainEvent;
    private GWTDomain<GWTPropertyDescriptor> domainVial;
    private GWTDomain<GWTPropertyDescriptor> domainSpecimen;

    private boolean _dirty;
    private List<PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor>> _domainEditors = new ArrayList<PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor>>();
    private HTML _statusLabel = new HTML("<br/>");
    private static final String STATUS_SUCCESSFUL = "Save successful.<br/>";
    private final String _returnURL;
    private SaveButtonBar saveBarTop;
    private SaveButtonBar saveBarBottom;
    private HandlerRegistration _closeHandlerManager;
    private String _designerURL;
    private boolean _showing = false;

    private boolean _saveInProgress = false;

    public SpecimenDesignerMainPanel(RootPanel rootPanel)
    {
        _rootPanel = rootPanel;

        _designerURL = Window.Location.getHref();
        _returnURL = PropertyUtil.getReturnURL();
    }

    public void showAsync()
    {
        _rootPanel.clear();
        _rootPanel.add(new Label("Loading..."));

        getService().getDomainDescriptors(new AsyncCallback<List<GWTDomain<GWTPropertyDescriptor>>>()
        {
            public void onFailure(Throwable throwable)
            {
                addErrorMessage("Unable to load specimen properties definition: " + throwable.getMessage());
            }

            public void onSuccess(List<GWTDomain<GWTPropertyDescriptor>> domains)
            {
                domainEvent = domains.get(0);
                domainVial = domains.get(1);
                domainSpecimen = domains.get(2);
                show();
            }
        });
    }


    private SpecimenServiceAsync getService()
    {
        if (_testService == null)
        {
            _testService = GWT.create(SpecimenService.class);
            ServiceUtil.configureEndpoint(_testService, "service", "study-samples");
        }
        return _testService;
    }


    private void show()
    {
        _showing = true;
        _rootPanel.clear();
        _domainEditors.clear();
        saveBarTop = new SaveButtonBar(this);
        _rootPanel.add(saveBarTop);
        _rootPanel.add(_statusLabel);

        for (GWTDomain<GWTPropertyDescriptor> domain : Arrays.asList(domainEvent, domainVial, domainSpecimen))
        {
            _rootPanel.add(new HTML("<br/>"));

            // Make sure required properties cannot be edited or moved
            for (GWTPropertyDescriptor property : domain.getFields())
                if (property.isRequired())
                {
                    property.setDisableEditing(true);
                    property.setPreventReordering(true);
                }

            PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor> editor =
                    new PropertiesEditor.PD(_rootPanel, null, getService());
            editor.addChangeHandler(new ChangeHandler()
            {
                public void onChange(ChangeEvent e)
                {
                    if (!isShowing())
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

        _closeHandlerManager = Window.addWindowClosingHandler(new AssayCloseListener());
        _showing = false;
    }

    private boolean isShowing()
    {
        return _showing;
    }

    protected void addErrorMessage(String message)
    {
        VerticalPanel mainPanel = new VerticalPanel();
        mainPanel.add(new Label(message));
        _rootPanel.add(mainPanel);
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
        if (_saveInProgress)
        {
            if (saveBarTop != null)
                saveBarTop.disableAll();
            if (saveBarBottom != null)
                saveBarBottom.disableAll();
        }
        else
        {
            if (saveBarTop != null)
                saveBarTop.setAllowSave(dirty);
            if (saveBarBottom != null)
                saveBarBottom.setAllowSave(dirty);
        }
    }

    private void validate(final AsyncCallback<List<String>> callback, final boolean saveAndClose)
    {
        final List<String> errors = new ArrayList<String>();

        // Get the errors for each of the PropertiesEditors
        for (PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor> propeditor : _domainEditors)
        {
            List<String> domainErrors = propeditor.validate();
            if (domainErrors.size() > 0)
                errors.addAll(domainErrors);
            }

        // Check for the same name in Specimen and Vial
        Set<String> specimenFields = new HashSet<String>();
        List<GWTPropertyDescriptor> optionalSpecimenFields = new ArrayList<GWTPropertyDescriptor>();
        for (GWTPropertyDescriptor prop : domainSpecimen.getFields())
        {
            if (null != prop.getName())
            {
                specimenFields.add(prop.getName().toLowerCase());
                if (!prop.isRequired())
                {
                    optionalSpecimenFields.add(prop);
                    if (prop.getName().contains(" "))
                        errors.add("Name '" + prop.getName() + "' should not contain spaces.");
                }
            }
        }

        Set<String> vialFields = new HashSet<String>();
        List<GWTPropertyDescriptor> optionalVialFields = new ArrayList<GWTPropertyDescriptor>();
        for (GWTPropertyDescriptor prop : domainVial.getFields())
        {
            if (null != prop.getName())
            {
                if (!prop.isRequired() && specimenFields.contains(prop.getName().toLowerCase()))
                    errors.add("Vial cannot have a custom field of the same name as a Specimen field: " + prop.getName());
                else
                    vialFields.add(prop.getName().toLowerCase());       // only add if we aren't already reporting error on that name

                if (!prop.isRequired())
                {
                    optionalVialFields.add(prop);
                    if (prop.getName().contains(" "))
                        errors.add("Name '" + prop.getName() + "' should not contain spaces.");
                }
            }
        }

        List<GWTPropertyDescriptor> optionalEventFields = new ArrayList<GWTPropertyDescriptor>();
        for (GWTPropertyDescriptor prop : domainEvent.getFields())
            if (!prop.isRequired())
            {
                optionalEventFields.add(prop);
                if (prop.getName().contains(" "))
                    errors.add("Name '" + prop.getName() + "' should not contain spaces.");
            }

        for (GWTPropertyDescriptor prop : domainSpecimen.getFields())
        {
            if (!prop.isRequired() && null != prop.getName() && vialFields.contains(prop.getName().toLowerCase()))
                errors.add("Specimen cannot have a custom field of the same name as a Vial field: " + prop.getName());
        }

        if (errors.size() > 0)
        {
            String sep = "";
            String errorString = "";
            for (String error : errors)
            {
                errorString += sep + error;
                sep = "\n";
            }
            Window.alert(errorString);
            return;
        }

        // Ask server to check rollups
        getService().checkRollups(optionalEventFields, optionalVialFields, optionalSpecimenFields,
            new AsyncCallback<List<String>>()
            {
                public void onFailure(Throwable caught)
                {
                }

                public void onSuccess(List<String> result)
                {
                    String sep = "";
                    String errorString = "";
                    for (String warn : result)
                    {
                        errorString += sep + warn;
                        sep = "   ";
                    }
                    boolean done = true;
                    if (result.size() > 0)
                    {
                        String message = saveAndClose ? "\n\nClick OK to ignore; Cancel to defer saving and stay on the page." :
                                                        "\n\nClick OK to ignore; Cancel to defer saving.";
                        done = Window.confirm("Warning:\n\n" + errorString + message);
                    }

                    if (done)
                        updateWithCallback(callback);
                }
            }
        );
    }

    public String getCurrentURL()
    {
        return _designerURL;
    }

    public void save()
    {
        save(null);
    }

    public void save(final SaveListener<List<String>> listener)
    {
        saveAsync(new ErrorDialogAsyncCallback<List<String>>("Save failed")
        {
            @Override
            protected void handleFailure(String message, Throwable caught)
            {
                _saveInProgress = false;
                setDirty(true);
            }

            public void onSuccess(List<String> result)
            {
                _saveInProgress = false;
                setDirty(false);
                _statusLabel.setHTML(STATUS_SUCCESSFUL);
                showAsync();

                if (listener != null)
                {
                    listener.saveSuccessful(result, _designerURL);
                }
            }
        }, false);
    }


    private void saveAsync(AsyncCallback<List<String>> callback, boolean saveAndClose)
    {
        validate(callback, saveAndClose);     // Will call updateWithCallback after asyncs if appropriate
    }

    private void updateWithCallback(AsyncCallback<List<String>> callback)
    {
        GWTDomain<GWTPropertyDescriptor> updateEvent = _domainEditors.get(0).getUpdates();
        GWTDomain<GWTPropertyDescriptor> updateVial = _domainEditors.get(1).getUpdates();
        GWTDomain<GWTPropertyDescriptor> updateSpecimen = _domainEditors.get(2).getUpdates();

        getService().updateDomainDescriptors(
                updateEvent,
                updateVial,
                updateSpecimen,
                callback
        );
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
            url = PropertyUtil.getContextPath() + "/study" + PropertyUtil.getContainerPath() + "/manageStudy.view";
        }
        WindowUtil.setLocation(url);
    }


    public void finish()
    {
        final String doneLink;
        if (_returnURL != null)
            doneLink = _returnURL;
        else
            doneLink = PropertyUtil.getContextPath() + "/study" + PropertyUtil.getContainerPath() + "/manageStudy.view";

        if (!_dirty)
        {
            // No need to save
            WindowUtil.setLocation(doneLink);
        }
        else
        {
            saveAsync(new ErrorDialogAsyncCallback<List<String>>("Save failed")
            {
                @Override
                protected void handleFailure(String message, Throwable caught)
                {
                    _saveInProgress = false;
                    setDirty(true);
                }
                
                public void onSuccess(List<String> result)
                {
                    if (_closeHandlerManager != null)
                    {
                        _closeHandlerManager.removeHandler();
                        _closeHandlerManager = null;
                    }
                    WindowUtil.setLocation(doneLink);
                }
            }, true);
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
