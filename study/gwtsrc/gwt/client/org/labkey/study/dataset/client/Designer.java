/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

package gwt.client.org.labkey.study.dataset.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.ChangeListener;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FocusListenerAdapter;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.HTMLTable;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.KeyboardListenerAdapter;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import gwt.client.org.labkey.study.StudyApplication;
import gwt.client.org.labkey.study.dataset.client.model.GWTDataset;
import org.labkey.api.gwt.client.PHIType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.PropertiesEditor;
import org.labkey.api.gwt.client.ui.Saveable;
import org.labkey.api.gwt.client.ui.WebPartPanel;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User: matthewb
 * Date: Apr 24, 2007
 * Time: 9:24:04 AM
 */
public class Designer implements EntryPoint, Saveable<GWTDataset>
{
    private String _returnURL;
    private String _cancelURL;
    private String _timepointType;

    private GWTDataset _dataset;
    private GWTDomain _domain;

    // UI bits
    private RootPanel _root = null;
    private FlexTable _buttons = null;
    private Label _loading = null;
    private PropertiesEditor _propTable = null;
    boolean _saved = false;

    private DatasetProperties _propertiesPanel;
    private DatasetSchema _schemaPanel;
    private boolean _dirty;

    private SubmitButton _saveButton;

    private class DomainDatasetSaveable implements Saveable<GWTDomain>
    {
        private Saveable<GWTDataset> _datasetSaveable;

        public DomainDatasetSaveable(Saveable<GWTDataset> datasetSaveable)
        {
            _datasetSaveable = datasetSaveable;
        }

        public String getCurrentURL()
        {
            return PropertyUtil.getCurrentURL();
        }

        public void save()
        {
            _datasetSaveable.save();
        }

        public void save(final SaveListener<GWTDomain> gwtDomainSaveListener)
        {
            // okay, this gets a bit mind bending.  When asked to save, the GWTDomain saveable delegates
            // to the GWTDataset Saveable.  When the dataset reply comes back, the GWTDataset Saveable must
            // make an async call to get the GWT Domain so we can forward the successful save message back
            // to the original GWT Domain saveable.
            _datasetSaveable.save(new SaveListener<GWTDataset>()
            {
                public void saveSuccessful(GWTDataset datasetResult, String designerUrl)
                {
                    asyncGetDefinition(datasetResult.getTypeURI(), gwtDomainSaveListener);
                }
            });
        }

        public void cancel()
        {
            _datasetSaveable.cancel();
        }

        public void finish()
        {
            _datasetSaveable.finish();
        }

        public boolean isDirty()
        {
            return _datasetSaveable.isDirty();
        }
    }


    public void onModuleLoad()
    {
        int datasetId = Integer.parseInt(PropertyUtil.getServerProperty("datasetId"));
        String typeURI = PropertyUtil.getServerProperty("typeURI");
        _returnURL = PropertyUtil.getReturnURL();
        _cancelURL = PropertyUtil.getCancelURL();
        _timepointType = PropertyUtil.getServerProperty("timepointType");

        _root = StudyApplication.getRootPanel();

        _loading = new Label("Loading...");

        _propTable = new PropertiesEditor.PD(_root, new DomainDatasetSaveable(this), getService(), true);

        _buttons = new FlexTable();
        _buttons.getElement().setClassName("gwt-ButtonBar");

        _saveButton = new SubmitButton();

        _buttons.setWidget(0, 0, _saveButton);
        _buttons.setWidget(0, 1, new CancelButton());

        _root.add(_loading);


        // NOTE for now we're displaying dataset info w/ static HTML
        asyncGetDataset(datasetId);
        asyncGetDefinition(typeURI);

        Window.addWindowClosingHandler(new Window.ClosingHandler()
        {
            public void onWindowClosing(Window.ClosingEvent event)
            {
                if (isDirty())
                    event.setMessage("Changes have not been saved and will be discarded.");
            }
        });
    }

    private void setDirty(boolean dirty)
    {
        _dirty = dirty;
    }

    public boolean isDirty()
    {
        return !_saved && (_dirty || _propTable.isDirty());
    }

    public void setDataset(GWTDataset ds)
    {
        _dataset = ds;

        if (_dataset != null && _dataset.getSourceAssayName() != null)
            _propTable.setReadOnly(true);

        showUI();
    }


    public void setDomain(GWTDomain d)
    {
        if (null == _root)
            return;

        _domain = d;

        _propTable.init(new GWTDomain(d));
        if (null == d.getFields() || d.getFields().size() == 0)
            _propTable.addField(new GWTPropertyDescriptor());

        showUI();
    }


    private void showUI()
    {
        if (null != _domain && null != _dataset)
        {
            if (_dataset.getKeyPropertyName() != null)
            {
                _domain.setPhiNotAllowedFieldNames(Collections.singleton(_dataset.getKeyPropertyName()));
                _propTable.init(new GWTDomain(_domain));
            }
            _root.remove(_loading);
            _root.add(_buttons);

            _propertiesPanel = new DatasetProperties();
            _root.add(new WebPartPanel("Dataset Properties", _propertiesPanel));

            _schemaPanel = new DatasetSchema(_propTable);
            _root.add(new WebPartPanel("Dataset Fields", _schemaPanel));
        }
    }

    class SubmitButton extends ImageButton
    {
        SubmitButton()
        {
            super("Save");
        }

        public void onClick(Widget sender)
        {
            finish();
        }
    }


    class CancelButton extends ImageButton
    {
        CancelButton()
        {
            super("Cancel");
        }

        public void onClick(Widget sender)
        {
            if (_cancelURL != null && _cancelURL.length() > 0)
                navigate(_cancelURL);
            else
                cancel();
        }
    }

    public void save(final SaveListener<GWTDataset> listener)
    {
        // bug 6898: prevent user from double-clicking on save button, causing a race condition
        _saveButton.setEnabled(false);

        List<String> errors = new ArrayList<String>();

        _propertiesPanel.validate(errors);
        _schemaPanel.validate(errors);

        if (!errors.isEmpty())
        {
            StringBuilder sb = new StringBuilder();
            for (String error : errors)
                sb.append(error).append("\n");
            Window.alert(sb.toString());
            _saveButton.setEnabled(true);
            return;
        }

        AsyncCallback<List<String>> callback = new ErrorDialogAsyncCallback<List<String>>() {
            public void handleFailure(String message, Throwable caught)
            {
                _saveButton.setEnabled(true);
            }

            public void onSuccess(List<String> errors)
            {
                if (errors.isEmpty())
                {
                    _saved = true;  // avoid popup warning
                    if (listener != null)
                        listener.saveSuccessful(_dataset, PropertyUtil.getCurrentURL());
                }
                else
                {
                    StringBuilder sb = new StringBuilder();
                    for (String error : errors)
                        sb.append(error).append("\n");
                    Window.alert(sb.toString());
                    _saveButton.setEnabled(true);
                }
            }
        };

        getService().updateDatasetDefinition(_dataset, _domain, _propTable.getUpdates(), callback);
    }

    public String getCurrentURL()
    {
        return PropertyUtil.getCurrentURL();
    }

    public void save()
    {
        save(null);
    }

    public void cancel()
    {
        back();
    }

    public void finish()
    {
        save(new SaveListener<GWTDataset>()
        {
            public void saveSuccessful(GWTDataset dataset, String designerUrl)
            {
                if (null == _returnURL || _returnURL.length() == 0)
                    cancel();
                else
                    navigate(_returnURL);
            }
        });
    }

    public static native void navigate(String url) /*-{
      $wnd.location.href = url;
    }-*/;


    public static native void back() /*-{
        $wnd.history.back();
    }-*/;


    /*
     * SERVER CALLBACKS
     */

    private DatasetServiceAsync _service = null;
    private DatasetServiceAsync getService()
    {
        if (_service == null)
        {
            _service = GWT.create(DatasetService.class);
            ServiceUtil.configureEndpoint(_service, "datasetService");
        }
        return _service;
    }


    void asyncGetDataset(int id)
    {
        getService().getDataset(id, new ErrorDialogAsyncCallback<GWTDataset>()
        {
                public void handleFailure(String message, Throwable caught)
                {
                    _loading.setText("ERROR: " + message);
                }

                public void onSuccess(GWTDataset result)
                {
                    setDataset(result);
                }
        });
    }

    void asyncGetDefinition(final String domainURI)
    {
        asyncGetDefinition(domainURI, null);
    }

    void asyncGetDefinition(final String domainURI, final Saveable.SaveListener<GWTDomain> saveListener)
    {
        if (!domainURI.equals("testURI#TYPE"))
        {
            getService().getDomainDescriptor(domainURI, new ErrorDialogAsyncCallback<GWTDomain>()
            {
                    public void handleFailure(String message, Throwable caught)
                    {
                        _loading.setText("ERROR: " + caught.getMessage());
                    }

                    public void onSuccess(GWTDomain result)
                    {
                        GWTDomain domain = result;
                        if (null == domain)
                        {
                            domain = new GWTDomain();
                            Window.alert("Domain not defined: " + domainURI);
                        }

                        setDomain(domain);

                        if (saveListener != null)
                            saveListener.saveSuccessful(domain, PropertyUtil.getCurrentURL());
                    }
            });
        }
        else
        {
            GWTDomain domain = new GWTDomain();
            domain.setDomainURI(domainURI);
            domain.setName("DEM");
            domain.setDescription("I'm a description");

            List<GWTPropertyDescriptor> list = new ArrayList<GWTPropertyDescriptor>();

            GWTPropertyDescriptor p = new GWTPropertyDescriptor();
            p.setName("ParticipantID");
            p.setPropertyURI(domainURI + "." + p.getName());
            p.setRangeURI("xsd:double");
            p.setRequired(true);
            list.add(p);

            p = new GWTPropertyDescriptor();
            p.setName("SequenceNum");
            p.setPropertyURI(domainURI + "." + p.getName());
            p.setRangeURI("xsd:double");
            p.setRequired(true);
            list.add(p);

            p = new GWTPropertyDescriptor();
            p.setPropertyId(2);
            p.setName("DEMsex");
            p.setPropertyURI(domainURI + "." + p.getName());
            p.setRangeURI("xsd:int");
            list.add(p);

            p = new GWTPropertyDescriptor();
            p.setPropertyId(3);
            p.setName("DEMhr");
            p.setPropertyURI(domainURI + "." + p.getName());
            p.setRangeURI("xsd:int");
            list.add(p);

            domain.setFields(list);
            setDomain(domain);
        }
    }

    private interface WidgetUpdatable
    {
        void update(Widget widget);
    }

    private class BoundTextBox extends HorizontalPanel
    {
        protected TextBox _box;
        public BoundTextBox(String name, String initialValue, final WidgetUpdatable updatable)
        {
            _box = new TextBox();
            _box.setText(initialValue);
            _box.setName(name);
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

        public void setEnabled(boolean enabled)
        {
            _box.setEnabled(enabled);
        }
    }

    private class BoundCheckBox extends CheckBox
    {
        public BoundCheckBox(String label, boolean checked, final WidgetUpdatable updatable)
        {
            super(label);

            setChecked(checked);

            addFocusListener(new FocusListenerAdapter()
            {
                public void onLostFocus(Widget sender)
                {
                    updatable.update(sender);
                }
            });
            addClickListener(new ClickListener()
            {
                public void onClick(Widget sender)
                {
                    updatable.update(sender);
                    setDirty(true);
                }
            });
        }
    }

    private class BoundTextArea extends TextArea
    {
        public BoundTextArea()
        {
            this(null, null);
        }

        public BoundTextArea(String text, final WidgetUpdatable updatable)
        {
            super();
            setCharacterWidth(40);
            setHeight("10em");
            if (text != null)
                setText(text);

            addKeyboardListener(new KeyboardListenerAdapter()
            {
                public void onKeyPress(Widget sender, char keyCode, int modifiers)
                {
                    setDirty(true);
                }
            });

            if (updatable != null)
                addChangeListener(new ChangeListener()
                {
                    public void onChange(Widget sender)
                    {
                        updatable.update(sender);
                    }
                });
        }
    }

    private class BoundListBox extends ListBox
    {
        public BoundListBox(final WidgetUpdatable updatable)
        {
            super();
            addChangeListener(new ChangeListener()
            {
                public void onChange(Widget sender)
                {
                    updatable.update(sender);
                    setDirty(true);
                }
            });
        }

        public BoundListBox(Map<String,String> columns, String selected, final WidgetUpdatable updatable)
        {
            super();

            setVisibleItemCount(1);
            setColumns(columns);
            selectItem(selected);

            addChangeListener(new ChangeListener()
            {
                public void onChange(Widget sender)
                {
                    updatable.update(sender);
                    setDirty(true);
                }
            });
        }

        public String getSelectedValue()
        {
            return getValue(getSelectedIndex());
        }

        public void setColumns(Map<String,String> columns)
        {
            clear();
            for (Map.Entry<String,String> entry : columns.entrySet())
            {
                addItem(entry.getKey(), entry.getValue());
            }
        }

        public void selectItem(String text)
        {
            text = text != null ? text : "";
            for (int i=0; i < getItemCount(); i++)
            {
                String itemValue = getValue(i);
                if (text.equals(itemValue))
                {
                    setItemSelected(i, true);
                    return;
                }
            }
        }
    }

    private class DatasetProperties extends VerticalPanel
    {
        final FlexTable _table = new FlexTable();
        RadioButton _noneButton;

        public DatasetProperties()
        {
            super();
            createPanel();
        }

        private void createPanel()
        {
            _table.setStyleName("lk-fields-table");
            boolean fromAssay = _dataset.getSourceAssayName() != null;

            String labelStyleName="labkey-form-label"; // Pretty yellow background for labels
            HTMLTable.CellFormatter cellFormatter = _table.getCellFormatter();

            int row = 0;

            if (_dataset.getSourceAssayName() != null)
            {
                HorizontalPanel hPanel = new HorizontalPanel();
                hPanel.setVerticalAlignment(ALIGN_MIDDLE);
                hPanel.setSpacing(2);

                String assaySourceString = "This dataset is linked to <a href=\"" + _dataset.getSourceAssayURL() +
                "\">" + _dataset.getSourceAssayName() + "</a>.";
                HTML assaySourceHTML = new HTML(assaySourceString);
                hPanel.add(assaySourceHTML);

                Label helpPopup = new HelpPopup("Assay Link", "This dataset was created by copying assay data. Data can only be imported via that assay.");
                hPanel.add(helpPopup);
            }
            add(_table);

            BoundTextBox dsName = new BoundTextBox("dsName", _dataset.getName(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _dataset.setName(((TextBox)widget).getText());
                }
            });
            if (fromAssay)
                dsName.setEnabled(false);
            DOM.setElementAttribute(dsName._box.getElement(), "id", "DatasetDesignerName");
            HorizontalPanel panel = new HorizontalPanel();
            panel.add(new Label("Name"));
            panel.add(new HelpPopup("Name", "Short unique name, e.g. 'DEM1'"));
            _table.setWidget(row, 0, panel);
            cellFormatter.setStyleName(row, 0, labelStyleName);
            _table.setWidget(row, 1, dsName);

            TextBox dsId = new TextBox();
            dsId.setText(Integer.toString(_dataset.getDatasetId()));
            dsId.setEnabled(false);

            _table.setHTML(row, 2, "ID");
            cellFormatter.setStyleName(row, 2, labelStyleName);
            _table.setWidget(row++, 3, dsId);

            BoundTextBox dsLabel = new BoundTextBox("dsLabel", _dataset.getLabel(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _dataset.setLabel(((TextBox)widget).getText());
                }
            });
            panel = new HorizontalPanel();
            panel.add(new Label("Label"));
            panel.add(new HelpPopup("Label", "Descriptive label, e.g. 'Demographics form 1'"));

            _table.setWidget(row, 0, panel);
            cellFormatter.setStyleName(row, 0, labelStyleName);
            _table.setWidget(row, 1, dsLabel);

            BoundTextBox dsCategory = new BoundTextBox("dsCategory", _dataset.getCategory(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _dataset.setCategory(((TextBox)widget).getText());
                }
            });
            panel = new HorizontalPanel();
            panel.add(new Label("Category"));
            panel.add(new HelpPopup("Dataset Category", "Datasets with the same category name are shown together in the study navigator and dataset list."));
            _table.setWidget(row, 2, panel);
            cellFormatter.setStyleName(row, 2, labelStyleName);
            _table.setWidget(row++, 3, dsCategory);

            BoundTextBox tag = new BoundTextBox("tag", _dataset.getTag(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _dataset.setTag(((TextBox)widget).getText());
                }
            });

            panel = new HorizontalPanel();
            panel.add(new Label("Tag"));
            panel.add(new HelpPopup("Tag", "Adding a tag provides an additional, flexible way to categorize this dataset."));
            _table.setWidget(row, 2, panel);
            cellFormatter.setStyleName(row, 2, labelStyleName);
            _table.setWidget(row++, 3, tag);

            String selection = null;
            if (_dataset.getCohortId() != null)
                selection = _dataset.getCohortId().toString();
            BoundListBox dsCohort = new BoundListBox(_dataset.getCohortMap(), selection, new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    ListBox lb = (ListBox)widget;
                    if (lb.getSelectedIndex() != -1)
                    {
                        String value = lb.getValue(lb.getSelectedIndex());
                        if (value.length() > 0)
                            _dataset.setCohortId(Integer.valueOf(value));
                        else
                            _dataset.setCohortId(null);
                    }
                }
            });
            panel = new HorizontalPanel();
            panel.add(new Label("Cohort Association"));
            _table.setWidget(row, 0, panel);
            cellFormatter.setStyleName(row, 0, labelStyleName);

            if (!_dataset.getCohortMap().isEmpty())
                _table.setWidget(row, 1, dsCohort);
            else
                _table.setWidget(row, 1, new HTMLPanel("<em>No cohorts defined</em>"));

            if ("VISIT".equals(_timepointType))
            {
                BoundListBox dsVisitDate = new BoundListBox(_dataset.getVisitDateMap(), _dataset.getVisitDatePropertyName(), new WidgetUpdatable()
                {
                    public void update(Widget widget)
                    {
                        ListBox lb = (ListBox)widget;
                        if (lb.getSelectedIndex() != -1)
                        {
                            _dataset.setVisitDatePropertyName(lb.getValue(lb.getSelectedIndex()));
                        }
                    }
                });
                panel = new HorizontalPanel();
                panel.add(new Label("Visit Date Column"));
                panel.add(new HelpPopup("Visit Date Column", "If the official 'Visit Date' for a visit can come from this dataset, choose the date column to represent it. Note that since datasets can include data from many visits, each visit must also indicate the official 'VisitDate' dataset."));
                _table.setWidget(row, 2, panel);
                cellFormatter.setStyleName(row, 2, labelStyleName);
                _table.setWidget(row++, 3, dsVisitDate);
            }
            else
                row++;

            panel = new HorizontalPanel();
            panel.add(new Label("Additional Key Column"));
            panel.add(new HelpPopup("Additional Key",
                    "If dataset has more than one row per participant/visit, " +
                    "an additional key field must be provided. There " +
                    "can be at most one row in the dataset for each " +
                    "combination of participant, visit and key. " +
                    "<ul><li>None: No additional key</li>" +
                    "<li>Data Field: A user-managed key field</li>" +
                    "<li>Managed Field: A numeric or string field defined below will be managed" +
                    "by the server to make each new entry unique. Numbers will be " +
                    "assigned auto-incrementing integer values, strings will be assigned " +
                    "globally unique identifiers (GUIDs).</li>" +
                    "</ul>"));

            cellFormatter.setStyleName(row, 0, labelStyleName);
            _table.setWidget(row, 0, panel);

            // ADDITIONAL KEY COLUMN
            VerticalPanel vPanel = new VerticalPanel();
            vPanel.setSpacing(1);
            panel = new HorizontalPanel();
            panel.setSpacing(2);

            _noneButton = new RadioButton("additionalKey", "None");
            _noneButton.setChecked(_dataset.getKeyPropertyName() == null);
            setCheckboxId(_noneButton.getElement(), "button_none");
            if (fromAssay || _dataset.getDemographicData())
                _noneButton.setEnabled(false);
            panel.add(_noneButton);
            vPanel.add(panel);
            
            panel = new HorizontalPanel();
            panel.setSpacing(2);
            panel.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);
            final RadioButton dataFieldButton = new RadioButton("additionalKey", "Data Field:");
            setCheckboxId(dataFieldButton.getElement(), "button_dataField");
            if (fromAssay || _dataset.getDemographicData())
                dataFieldButton.setEnabled(false);
            dataFieldButton.setChecked(_dataset.getKeyPropertyName() != null && !_dataset.getKeyPropertyManaged());
            panel.add(dataFieldButton);

            final BoundListBox dataFieldsBox = new BoundListBox(new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _dataset.setKeyPropertyManaged(false);
                    _dataset.setKeyPropertyName(((BoundListBox)widget).getSelectedValue());
                    onAdditionalKeyFieldChanged();
                }
            });
            DOM.setElementAttribute(dataFieldsBox.getElement(), "id", "list_dataField");
            dataFieldsBox.setEnabled(!fromAssay && !_dataset.getDemographicData() && !_dataset.getKeyPropertyManaged() && _dataset.getKeyPropertyName() != null);

            panel.add(dataFieldsBox);
            vPanel.add(panel);

            panel = new HorizontalPanel();
            panel.setSpacing(2);
            panel.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);
            final RadioButton managedButton = new RadioButton("additionalKey", "Managed Field:");
            setCheckboxId(managedButton.getElement(), "button_managedField");
            if (fromAssay || _dataset.getDemographicData())
                managedButton.setEnabled(false);
            managedButton.setChecked(_dataset.getKeyPropertyManaged());
            panel.add(managedButton);

            final BoundListBox managedFieldsBox = new BoundListBox(new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _dataset.setKeyPropertyManaged(true);
                    _dataset.setKeyPropertyName(((BoundListBox)widget).getSelectedValue());
                    onAdditionalKeyFieldChanged();
                }
            });
            DOM.setElementAttribute(managedFieldsBox.getElement(), "id", "list_managedField");
            managedFieldsBox.setEnabled(_dataset.getKeyPropertyManaged() && !fromAssay && !_dataset.getDemographicData());

            panel.add(managedFieldsBox);
            vPanel.add(panel);
            _table.setWidget(row, 1, vPanel);

            BoundTextArea description = new BoundTextArea(_dataset.getDescription(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _dataset.setDescription(((TextArea)widget).getText());
                }
            });
            description.setName("description");
            panel = new HorizontalPanel();
            panel.add(new Label("Description"));
            _table.setWidget(row, 2, panel);
            cellFormatter.setStyleName(row, 2, labelStyleName);
            _table.setWidget(row, 3, description);
            
            //noinspection UnusedAssignment
            _table.getFlexCellFormatter().setRowSpan(row, 2, 3);
            _table.getFlexCellFormatter().setRowSpan(row++, 3, 3);


            // Listen to the list of properties
            _propTable.addChangeHandler(new ChangeHandler()
            {
                public void onChange(ChangeEvent e)
                {
                    resetKeyListBoxes(dataFieldsBox, managedFieldsBox);
                }
            });

            ClickListener buttonListener = new ClickListener()
            {
                public void onClick(Widget sender)
                {
                    if (sender == _noneButton)
                    {
                        _dataset.setKeyPropertyName(null);
                        _dataset.setKeyPropertyManaged(false);
                        dataFieldsBox.setEnabled(false);
                        managedFieldsBox.setEnabled(false);
                        onAdditionalKeyFieldChanged();
                    }
                    else if (sender == dataFieldButton)
                    {
                        _dataset.setKeyPropertyName(dataFieldsBox.getSelectedValue());
                        _dataset.setKeyPropertyManaged(false);
                        dataFieldsBox.setEnabled(true);
                        managedFieldsBox.setEnabled(false);
                        onAdditionalKeyFieldChanged();
                    }
                    else if (sender == managedButton)
                    {
                        _dataset.setKeyPropertyName(managedFieldsBox.getSelectedValue());
                        _dataset.setKeyPropertyManaged(true);
                        dataFieldsBox.setEnabled(false);
                        managedFieldsBox.setEnabled(true);
                        onAdditionalKeyFieldChanged();
                    }
                }
            };
            if (!fromAssay)
            {
                _noneButton.addClickListener(buttonListener);
                dataFieldButton.addClickListener(buttonListener);
                managedButton.addClickListener(buttonListener);
            }

            resetKeyListBoxes(dataFieldsBox, managedFieldsBox);
            // ADDITIONAL KEY COLUMN (end)


            BoundCheckBox demographicData = new BoundCheckBox("", _dataset.getDemographicData(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    boolean isDemographic = ((CheckBox)widget).isChecked();
                    _dataset.setDemographicData(isDemographic);
                    _noneButton.setEnabled(!isDemographic);
                    dataFieldButton.setEnabled(!isDemographic);
                    managedButton.setEnabled(!isDemographic);
                    dataFieldsBox.setEnabled(!isDemographic);
                    managedFieldsBox.setEnabled(!isDemographic);
                }
            });
            demographicData.setName("demographicData");
            if (fromAssay)
                demographicData.setEnabled(false);
            panel = new HorizontalPanel();
            panel.add(new Label("Demographic Data"));
            panel.add(new HelpPopup("Demographic Data", "Demographic data appears only once for each participant in the study."));
            _table.setWidget(row, 0, panel);
            cellFormatter.setStyleName(row, 0, labelStyleName);
            _table.setWidget(row++, 1, demographicData);


            // Only for project level datasets with shared definitions
            if (_dataset.isDefinitionShared() && _dataset.isVisitMapShared())
            {
                panel = new HorizontalPanel();
                panel.add(new Label("Share demographic data"));
                panel.add(new HelpPopup("Shared data across studies", "When 'No' is selected (default) each study folder 'owns' its own data rows.  If study has shared visits, then 'Share by Participants' means that data rows are shared across the project, and studies will only see data rows for participants that are part of that study."));
                _table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);

                Map<String,String> options = new LinkedHashMap<String,String>();
                options.put("No", "NONE");
                options.put("Share by Participants", "PTID");
                final BoundListBox sharedListBox = new BoundListBox(options,_dataset.getDataSharing(), new WidgetUpdatable()
                {
                    public void update(Widget widget)
                    {
                        _dataset.setDataSharing(((BoundListBox) widget).getSelectedValue());
                    }
                });
                sharedListBox.setEnabled(_dataset.getDemographicData());
                sharedListBox.setName("demographicsSharedBy");
                _table.setWidget(row++, 1, sharedListBox);

                // enable/disable according to isDemographicData setting
                demographicData.addValueChangeHandler(new ValueChangeHandler<Boolean>(){
                    public void onValueChange(ValueChangeEvent<Boolean> event)
                    {
                        boolean isDemographicData = event.getValue();
                        sharedListBox.setEnabled(isDemographicData);
                    }
                });
            }


            BoundCheckBox showByDefault = new BoundCheckBox("", _dataset.getShowByDefault(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _dataset.setShowByDefault(((CheckBox)widget).isChecked());
                }
            });
            showByDefault.setName("showByDefault");
            panel = new HorizontalPanel();
            panel.add(new Label("Show In Overview"));
            panel.add(new HelpPopup("Show In Overview", "When this item is checked, this dataset will show in the overview grid by default. It can be unhidden by clicking 'Show All Datasets.'"));
            _table.setWidget(row, 0, panel);
            cellFormatter.setStyleName(row, 0, labelStyleName);
            _table.setWidget(row++, 1, showByDefault);
        }

        private void onAdditionalKeyFieldChanged()
        {
            String keyPropertyName = _dataset.getKeyPropertyName();
            if (keyPropertyName != null && keyPropertyName.length() != 0)
            {
                _propTable.getDomain().setPhiNotAllowedFieldNames(Collections.singleton(keyPropertyName));
                GWTPropertyDescriptor prop = _propTable.getProperty(keyPropertyName);
                if (prop != null)
                {
                    prop.setPHI(PHIType.NotPHI.toString());
                }
            }
            else
            {
                _propTable.getDomain().setPhiNotAllowedFieldNames(Collections.emptySet());
            }
            _propTable.refresh();
        }

        private void setCheckboxId(Element e, String id)
        {
            NodeList list = e.getElementsByTagName("input");
            for (int i=0; i < list.getLength(); i++)
            {
                Node node = list.getItem(i);
                ((Element)node).setId(id);
            }
        }

        private void resetKeyListBoxes(BoundListBox dataFieldsBox, BoundListBox managedFieldsBox)
        {
            // Need to look up what properties have been defined
            // to populate the drop-down boxes for additional keys

            List<GWTPropertyDescriptor> descriptors = new ArrayList<GWTPropertyDescriptor>();
            for (int i=0; i<_propTable.getPropertyCount(); i++)
            {
                descriptors.add(_propTable.getPropertyDescriptor(i));
            }
            Map<String,String> fields = new HashMap<String,String>();
            Map<String,String> manageableFields = new HashMap<String,String>();

            for (GWTPropertyDescriptor descriptor : descriptors)
            {
                // Don't add deleted properties
                if (_propTable.getStatus(descriptor) == PropertiesEditor.FieldStatus.Deleted)
                    continue;

                String label = descriptor.getLabel();
                String name = descriptor.getName();
                if (name == null)
                    name = "";
                if (label == null || "".equals(label)) // if there's no label set, use the name for the drop-down
                    label = name;
                fields.put(label, name);

                String rangeURI = descriptor.getRangeURI();
                if (rangeURI.endsWith("int") || rangeURI.endsWith("double") || rangeURI.endsWith("string"))
                {
                    manageableFields.put(label, name);
                }
            }

            if (!"VISIT".equalsIgnoreCase(_timepointType))
                fields.put(GWTDataset.TIME_KEY_FIELD_DISPLAY, GWTDataset.TIME_KEY_FIELD_KEY);
            
            if (fields.size() == 0)
                fields.put("","");
            if (manageableFields.size() == 0)
                manageableFields.put("","");

            dataFieldsBox.setColumns(fields);
            managedFieldsBox.setColumns(manageableFields);

            String keyName = _dataset.getKeyPropertyName();
            if (keyName != null)
            {
                if (_dataset.getKeyPropertyManaged())
                {
                    managedFieldsBox.selectItem(keyName);

                    // It may not be there anymore; update
                    String selected = managedFieldsBox.getSelectedValue();
                    if (!keyName.equals(selected))
                    {
                        _dataset.setKeyPropertyName(selected);
                    }
                }
                else
                {
                    dataFieldsBox.selectItem(keyName);
                    // It may not be there anymore; update
                    String selected = dataFieldsBox.getSelectedValue();
                    if (!keyName.equals(selected))
                    {
                        _dataset.setKeyPropertyName(selected);
                    }
                }
            }
        }

        public void validate(List<String> errors)
        {
            if (_dataset.getName() == null || _dataset.getName().length() == 0)
                errors.add("Dataset name cannot be empty.");

            if (_dataset.getLabel() == null || _dataset.getLabel().length() == 0)
                errors.add("Dataset label cannot be empty.");

            if ("".equals(_dataset.getKeyPropertyName()))
                errors.add("Please select a field name for the additional key.");

            if ( (!_noneButton.isChecked()) && _dataset.getKeyPropertyName() == null )
                errors.add("Please select a field name for the additional key.");
        }
    }

    private class DatasetSchema extends FlexTable
    {
        private PropertiesEditor _propEdit;

        public DatasetSchema(PropertiesEditor propEdit)
        {
            super();
            _propEdit = propEdit;
            createPanel();
        }

        private void createPanel()
        {
            Widget propTable = _propEdit.getWidget();
            setWidget(0, 0, propTable);
        }

        public void validate(List<String> errors)
        {
            errors.addAll(_propEdit.validate());
        }
    }
}
