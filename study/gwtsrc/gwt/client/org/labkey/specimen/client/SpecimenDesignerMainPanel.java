/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import org.labkey.api.gwt.client.AbstractDesignerMainPanel;
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
import org.labkey.api.gwt.client.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SpecimenDesignerMainPanel extends AbstractDesignerMainPanel implements Saveable<List<String>>, DirtyCallback
{
    private SpecimenServiceAsync _testService;

    private GWTDomain<GWTPropertyDescriptor> domainEvent;
    private GWTDomain<GWTPropertyDescriptor> domainVial;
    private GWTDomain<GWTPropertyDescriptor> domainSpecimen;

    private HandlerRegistration _closeHandlerManager;
    private boolean _showing = false;

    private static final String COMMENTS = "Comments";                   // Reserved field name for Vial and Specimen
    private static final String COLUMN = "Column";                       // Reserved field name for Vial, Specimen and Event

    public SpecimenDesignerMainPanel(RootPanel rootPanel)
    {
        super(rootPanel);
        _returnURL = (null != _returnURL) ? _returnURL : PropertyUtil.getRelativeURL("manageStudy.view", "study");
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
            _rootPanel.add(panel);
        }

        saveBarBottom = new SaveButtonBar(this);
        _rootPanel.add(saveBarBottom);

        _closeHandlerManager = Window.addWindowClosingHandler(new DesignerClosingListener());
        _showing = false;
    }

    private boolean isShowing()
    {
        return _showing;
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
                    else if (COMMENTS.equalsIgnoreCase(prop.getName()) || COLUMN.equalsIgnoreCase(prop.getName()))
                        errors.add("Field name '" + prop.getName() + "' is reserved and may not be used in the Specimen table.");
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
                    else if (COMMENTS.equalsIgnoreCase(prop.getName()) || COLUMN.equalsIgnoreCase(prop.getName()))
                        errors.add("Field name '" + prop.getName() + "' is reserved and may not be used in the Vial table.");
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
                else if (COLUMN.equalsIgnoreCase(prop.getName()))
                    errors.add("Field name '" + prop.getName() + "' is reserved and may not be used in the SpecimenEvent table.");
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
                new AsyncCallback<List<List<String>>>()
                {
                    public void onFailure(Throwable caught)
                    {
                    }

                    public void onSuccess(List<List<String>> result)
                    {
                        // result list with 2 elements: list of errors, then list of warnings
                        if (!result.get(0).isEmpty())
                        {
                            String sep = "";
                            String errorString = "";
                            for (String error : result.get(0))
                            {
                                errorString += sep + error;
                                sep = "   ";
                            }
                            if (!StringUtils.isEmpty(errorString))
                            {
                                Window.alert(errorString);      // report errors and stay on page
                            }
                        }
                        else
                        {
                            boolean done = true;
                            if (!result.get(1).isEmpty())
                            {
                                String sep = "";
                                String errorString = "";
                                for (String warn : result.get(1))
                                {
                                    errorString += sep + warn;
                                    sep = "   ";
                                }

                                if (!StringUtils.isEmpty(errorString))
                                {
                                    String message = saveAndClose ? "\n\nClick OK to ignore; Cancel to defer saving and stay on the page." :
                                            "\n\nClick OK to ignore; Cancel to defer saving.";
                                    done = Window.confirm("Warning:\n\n" + errorString + message);
                                }
                            }

                            if (done)
                                updateWithCallback(callback);
                        }
                    }
                }
        );
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

        WindowUtil.setLocation(_returnURL);
    }


    public void finish()
    {
        final String doneLink = _returnURL;

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
