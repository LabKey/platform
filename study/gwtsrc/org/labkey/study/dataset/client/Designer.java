/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.study.dataset.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.WindowCloseListener;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.*;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.study.dataset.client.model.GWTDataset;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 24, 2007
 * Time: 9:24:04 AM
 */
public class Designer implements EntryPoint
{
    // data
    private int _studyId;
    private int _datasetId;
    private String _typeURI;
    private String _returnURL;
    private boolean _isDateBased;

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
    private boolean _create;
    private boolean _dirty;

    public void onModuleLoad()
    {
        _studyId = Integer.parseInt(PropertyUtil.getServerProperty("studyId"));
        _datasetId = Integer.parseInt(PropertyUtil.getServerProperty("datasetId"));
        _typeURI = PropertyUtil.getServerProperty("typeURI");
        _returnURL = PropertyUtil.getServerProperty("returnURL");
        String create = PropertyUtil.getServerProperty("create");
        if (create != null)
            _create = Boolean.valueOf(create).booleanValue();
        String dateBased = PropertyUtil.getServerProperty("dateBased");
        if (dateBased != null)
            _isDateBased = Boolean.valueOf(dateBased).booleanValue();

        _root = RootPanel.get("org.labkey.study.dataset.Designer-Root");

        _loading = new Label("Loading...");

        _propTable = new PropertiesEditor(getLookupService());
        _propTable.setMode(PropertiesEditor.modeEdit);

        _buttons = new FlexTable();
        _buttons.setWidget(0, 0, new CancelButton());
        _buttons.setWidget(0, 1, new SubmitButton());

        _root.add(_loading);


        // NOTE for now we're displaying dataset info w/ static HTML
        asyncGetDataset(_datasetId);
        asyncGetDefinition(_typeURI);
//        asyncGetDefinition("testURI#TYPE");

        Window.addWindowCloseListener(new WindowCloseListener()
        {
            public void onWindowClosed()
            {
            }

            public String onWindowClosing()
            {
                if (isDirty())
                    return "Changes have not been saved and will be discarded.";
                else
                    return null;
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

        showUI();
    }


    public void setDomain(GWTDomain d)
    {
        if (null == _root)
            return;

        _domain = d;

        _propTable.init(new GWTDomain(d));
        if (null == d.getPropertyDescriptors() || d.getPropertyDescriptors().size() == 0)
            _propTable.addPropertyDescriptor();

        showUI();
    }


    private void showUI()
    {
        if (null != _domain && null != _dataset)
        {
            _root.remove(_loading);
            _root.add(_buttons);

            _propertiesPanel = new DatasetProperties(_dataset);
            _root.add(new WebPartPanel("Dataset Properties", _propertiesPanel));

            _schemaPanel = new DatasetSchema(_propTable, _create);
            _root.add(new WebPartPanel("Dataset Schema", _schemaPanel));
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
            submitForm();
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
            cancelForm();
        }
    }


    private void submitForm()
    {
        List errors = new ArrayList();

        _propertiesPanel.validate(errors);
        _schemaPanel.validate(errors);

        if (!errors.isEmpty())
        {
            String s = "";
            for (int i=0 ; i < errors.size() ; i++)
                s += errors.get(i) + "\n";
            Window.alert(s);
            return;
        }

        AsyncCallback callback = new AsyncCallback() {
            public void onFailure(Throwable caught)
            {
                Window.alert(caught.getMessage());
            }

            public void onSuccess(Object result)
            {
                List errors = (List)result;
                if (null == errors)
                {
                    _saved = true;  // avoid popup warning
                    cancelForm();
                }
                else
                {
                    String s = "";
                    for (int i=0 ; i<errors.size() ; i++)
                        s += errors.get(i) + "\n";
                    Window.alert(s);
                }
            }
        };

        getService().updateDatasetDefinition(_dataset, _domain, _propTable.getDomainUpdates(), callback);
    }


    private void cancelForm()
    {
        if (null == _returnURL || _returnURL.length() == 0)
            back();
        else
            navigate(_returnURL);
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
            _service = (DatasetServiceAsync) GWT.create(DatasetService.class);
            ServiceUtil.configureEndpoint(_service, "datasetService");
        }
        return _service;
    }


    void asyncGetDataset(int id)
    {
        getService().getDataset(id, new AsyncCallback()
        {
                public void onFailure(Throwable caught)
                {
                    Window.alert(caught.getMessage());
                    _loading.setText("ERROR: " + caught.getMessage());
                }

                public void onSuccess(Object result)
                {
                    setDataset((GWTDataset)result);
                }
        });
    }


    void asyncGetDefinition(final String domainURI)
    {
        if (!domainURI.equals("testURI#TYPE"))
        {
            getService().getDomainDescriptor(domainURI, new AsyncCallback()
            {
                    public void onFailure(Throwable caught)
                    {
                        Window.alert(caught.getMessage());
                        _loading.setText("ERROR: " + caught.getMessage());
                    }

                    public void onSuccess(Object result)
                    {
                        GWTDomain domain = (GWTDomain)result;
                        if (null == domain)
                        {
                            domain = new GWTDomain();
                            Window.alert("Domain not defined: " + domainURI);
                        }

                        setDomain(domain);
                    }
            });
        }
        else
        {
            GWTDomain domain = new GWTDomain();
            domain.setDomainURI(domainURI);
            domain.setName("DEM");
            domain.setDescription("I'm a description");

            List list = new ArrayList();

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

            domain.setPropertyDescriptors(list);
            setDomain(domain);
        }
    }

    LookupServiceAsync getLookupService()
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
            setHeight("75px");
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

        public BoundListBox(Map columns, String selected, final WidgetUpdatable updatable)
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

        public void setColumns(Map columns)
        {
            clear();
            for (Iterator it = columns.entrySet().iterator(); it.hasNext();)
            {
                Map.Entry entry = (Map.Entry)it.next();
                addItem((String)entry.getKey(), (String)entry.getValue());
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

    private class DatasetProperties extends FlexTable
    {
        GWTDataset _dataset;
        RadioButton _noneButton;

        public DatasetProperties(GWTDataset dataset)
        {
            super();
            _dataset = dataset;
            createPanel();
        }

        private void createPanel()
        {
            String labelStyleName="labkey-form-label"; // Pretty yellow background for labels
            CellFormatter cellFormatter = getCellFormatter();

            int row = 0;

            BoundTextBox dsName = new BoundTextBox("dsName", _dataset.getName(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _dataset.setName(((TextBox)widget).getText());
                }
            });
            DOM.setElementAttribute(dsName._box.getElement(), "id", "DatasetDesignerName");
            HorizontalPanel panel = new HorizontalPanel();
            panel.add(new Label("Dataset Name"));
            panel.add(new HelpPopup("Name", "Short unique name, e.g. 'DEM1'"));
            setWidget(row, 0, panel);
            cellFormatter.setStyleName(row, 0, labelStyleName);
            setWidget(row, 1, dsName);

            TextBox dsId = new TextBox();
            dsId.setText(Integer.toString(_dataset.getDatasetId()));
            dsId.setEnabled(false);

            setHTML(row, 2, "Dataset Id");
            cellFormatter.setStyleName(row, 2, labelStyleName);
            setWidget(row++, 3, dsId);

            BoundTextBox dsLabel = new BoundTextBox("dsLabel", _dataset.getLabel(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _dataset.setLabel(((TextBox)widget).getText());
                }
            });
            panel = new HorizontalPanel();
            panel.add(new Label("Dataset Label"));
            panel.add(new HelpPopup("Label", "Descriptive label, e.g. 'Demographics form 1'"));

            setWidget(row, 0, panel);
            cellFormatter.setStyleName(row, 0, labelStyleName);
            setWidget(row, 1, dsLabel);

            BoundTextBox dsCategory = new BoundTextBox("dsCategory", _dataset.getCategory(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _dataset.setCategory(((TextBox)widget).getText());
                }
            });
            panel = new HorizontalPanel();
            panel.add(new Label("Dataset Category"));
            panel.add(new HelpPopup("Dataset Category", "Datasets with the same category name are shown together in the study navigator and dataset list."));
            setWidget(row, 2, panel);
            cellFormatter.setStyleName(row, 2, labelStyleName);
            setWidget(row++, 3, dsCategory);

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
            panel.add(new Label("Cohort"));
            setWidget(row, 0, panel);
            cellFormatter.setStyleName(row, 0, labelStyleName);

            if (!_dataset.getCohortMap().isEmpty())
                setWidget(row, 1, dsCohort);
            else
                setWidget(row, 1, new HTMLPanel("<em>No cohorts defined</em>"));

            if (!_isDateBased)
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
                setWidget(row, 2, panel);
                cellFormatter.setStyleName(row, 2, labelStyleName);
                setWidget(row++, 3, dsVisitDate);
            }
            else
                row++;

            panel = new HorizontalPanel();
            panel.add(new Label("Additional Key"));
            panel.add(new HelpPopup("Additional Key",
                    "If dataset has more than one row per participant/visit, " +
                            "an additional key field must be provided. There " +
                            "can be at most one row in the dataset for each " +
                            "combination of participant, visit and key. " +
                            "<ul><li>None: No additional key</li>" +
                            "<li>Data Field: A user-managed key field</li>" +
                            "<li>Managed Field: A numeric field defined below will be managed" +
                            "by the server to make each new entry unique</li>" +
                            "</ul>"));

            cellFormatter.setStyleName(row, 0, labelStyleName);
            setWidget(row, 0, panel);

            VerticalPanel vPanel = new VerticalPanel();
            panel = new HorizontalPanel();

            _noneButton = new RadioButton("additionalKey", "None");
            _noneButton.setChecked(_dataset.getKeyPropertyName() == null);
            DOM.setElementAttribute(_noneButton.getElement(), "id", "button_none");
            panel.add(_noneButton);
            vPanel.add(panel);



            panel = new HorizontalPanel();
            panel.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);
            final RadioButton dataFieldButton = new RadioButton("additionalKey", "Data Field ");
            DOM.setElementAttribute(dataFieldButton.getElement(), "id", "button_dataField");
            dataFieldButton.setChecked(_dataset.getKeyPropertyName() != null && !_dataset.getKeyPropertyManaged());
            panel.add(dataFieldButton);

            final BoundListBox dataFieldsBox = new BoundListBox(new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _dataset.setKeyPropertyManaged(false);
                    _dataset.setKeyPropertyName(((BoundListBox)widget).getSelectedValue());
                }
            });
            DOM.setElementAttribute(dataFieldsBox.getElement(), "id", "list_dataField");
            dataFieldsBox.setEnabled(!_dataset.getKeyPropertyManaged() && _dataset.getKeyPropertyName() != null);

            panel.add(dataFieldsBox);
            vPanel.add(panel);

            panel = new HorizontalPanel();
            panel.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);
            final RadioButton managedButton = new RadioButton("additionalKey", "Managed Field");
            DOM.setElementAttribute(managedButton.getElement(), "id", "button_managedField");
            managedButton.setChecked(_dataset.getKeyPropertyManaged());
            panel.add(managedButton);

            final BoundListBox managedFieldsBox = new BoundListBox(new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _dataset.setKeyPropertyManaged(true);
                    _dataset.setKeyPropertyName(((BoundListBox)widget).getSelectedValue());
                }
            });
            DOM.setElementAttribute(managedFieldsBox.getElement(), "id", "list_managedField");
            managedFieldsBox.setEnabled(_dataset.getKeyPropertyManaged());

            panel.add(managedFieldsBox);
            vPanel.add(panel);
            setWidget(row++, 1, vPanel);

            // Listen to the list of properties
            _propTable.addChangeListener(new ChangeListener()
            {
                public void onChange(Widget sender)
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
                    }
                    else if (sender == dataFieldButton)
                    {
                        _dataset.setKeyPropertyName(dataFieldsBox.getSelectedValue());
                        _dataset.setKeyPropertyManaged(false);
                        dataFieldsBox.setEnabled(true);
                        managedFieldsBox.setEnabled(false);

                    }
                    else if (sender == managedButton)
                    {
                        _dataset.setKeyPropertyName(managedFieldsBox.getSelectedValue());
                        _dataset.setKeyPropertyManaged(true);
                        dataFieldsBox.setEnabled(false);
                        managedFieldsBox.setEnabled(true);
                    }
                }
            };
            _noneButton.addClickListener(buttonListener);
            dataFieldButton.addClickListener(buttonListener);
            managedButton.addClickListener(buttonListener);

            resetKeyListBoxes(dataFieldsBox, managedFieldsBox);


            BoundCheckBox demographicData = new BoundCheckBox("", _dataset.getDemographicData(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _dataset.setDemographicData(((CheckBox)widget).isChecked());
                }
            });
            panel = new HorizontalPanel();
            panel.add(new Label("Demographic Data"));
            panel.add(new HelpPopup("Demographic Data", "Demographic data appears only once for each participant in the study."));
            setWidget(row, 0, panel);
            cellFormatter.setStyleName(row, 0, labelStyleName);
            setWidget(row++, 1, demographicData);

            BoundCheckBox showByDefault = new BoundCheckBox("", _dataset.getShowByDefault(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _dataset.setShowByDefault(((CheckBox)widget).isChecked());
                }
            });
            panel = new HorizontalPanel();
            panel.add(new Label("Show In Overview"));
            panel.add(new HelpPopup("Show In Overview", "When this item is checked, this dataset will show in the overview grid by default. It can be unhidden by clicking 'Show Hidden Data.'"));
            setWidget(row, 0, panel);
            cellFormatter.setStyleName(row, 0, labelStyleName);
            setWidget(row++, 1, showByDefault);

            BoundTextArea description = new BoundTextArea(_dataset.getDescription(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _dataset.setDescription(((TextArea)widget).getText());
                }
            });
            panel = new HorizontalPanel();
            panel.add(new Label("Description"));
            setWidget(row, 0, panel);
            cellFormatter.setStyleName(row, 0, labelStyleName);
            setWidget(row, 1, description);
            getFlexCellFormatter().setColSpan(row++, 1, 3);
        }

        private void resetKeyListBoxes(BoundListBox dataFieldsBox, BoundListBox managedFieldsBox)
        {
            // Need to look up what properties have been defined
            // to populate the drop-down boxes for additional keys

            List/*<GWTPropertyDescriptor>*/ descriptors = new ArrayList();
            for (int i=0; i<_propTable.getPropertyCount(); i++)
            {
                descriptors.add(_propTable.getPropertyDescriptor(i));
            }
            Map/*<String,String>*/ fields = new HashMap();
            Map/*<String,String>*/ numericFields = new HashMap();

            // Need to add empty items for both for no selection
            //fields.put("","");
            //numericFields.put("","");

            for (Iterator iter = descriptors.iterator(); iter.hasNext();)
            {
                GWTPropertyDescriptor descriptor = (GWTPropertyDescriptor)iter.next();

                // Don't add deleted properties
                if (_propTable.getStatus(descriptor).equals(PropertiesEditor.statusDeleted))
                    continue;

                String label = descriptor.getLabel();
                String name = descriptor.getName();
                if (name == null)
                    name = "";
                if (label == null || "".equals(label)) // if there's no label set, use the name for the drop-down
                    label = name;
                fields.put(label, name);

                String rangeURI = descriptor.getRangeURI();
                if (rangeURI.endsWith("int") || rangeURI.endsWith("double"))
                {
                    numericFields.put(label, name);
                }
            }
            if (fields.size() == 0)
                fields.put("","");
            if (numericFields.size() == 0)
                numericFields.put("","");

            dataFieldsBox.setColumns(fields);
            managedFieldsBox.setColumns(numericFields);

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

        public void validate(List errors)
        {
            if (_dataset.getName() == null || _dataset.getName().length() == 0)
                errors.add("Dataset name cannot be empty.");

            if ("".equals(_dataset.getKeyPropertyName()))
                errors.add("Please select a field name for the additional key.");

            if ( (!_noneButton.isChecked()) && _dataset.getKeyPropertyName() == null )
                errors.add("Please select a field name for the additional key.");
        }
    }

    private class DatasetSchema extends FlexTable
    {
        private PropertiesEditor _propEdit;
        private boolean _create;

        public DatasetSchema(PropertiesEditor propEdit, boolean create)
        {
            super();
            _propEdit = propEdit;
            _create = create;
            createPanel();
        }

        private void createPanel()
        {
            Widget propTable = _propEdit.getWidget();

            int row = 0;
            ImageButton importButton = new ImageButton("Import Schema", new ClickListener()
            {
                public void onClick(Widget sender)
                {
                    final TsvPopup popup = new TsvPopup();
                    popup.setText("Import Schema");
                    popup.center();
                }
            });
            setWidget(row++, 0, importButton);


            setWidget(row++, 0, propTable);
        }

        private class TsvPopup extends DialogBox
        {
            private BoundTextArea schemaTsv;

            private TsvPopup()
            {
                super(false, true);
                VerticalPanel vPanel = new VerticalPanel();
                vPanel.setSpacing(5);
                String warning = "<b>NOTE: This will replace any existing fields you have defined.</b>";
                if (!_create)
                {
                    warning = "<b><font class=\"labkey-error\">WARNING: This will replace any existing fields you have defined, " +
                            "and delete all data in any redefined fields!</font></b>";
                }
                HTML html = new HTML(
                        warning +
                        "<p><b>Paste tab-delimited text with the following column headers and one row for each field</b><br>\n" +
                        "<b>Property</b> - Required. Field name. Must start with a character and include only characters and numbers<br>\n" +
                        "<b>RangeURI</b> - Required. Values: xsd:int, xsd:string, xsd:double, xsd:boolean, xsd:dateTime<br>\n" +
                        "<b>Format</b> - Optional. Format for a date or numeric field<br>\n" +
                        "<b>Label</b> - Optional. Name that users will see for the field<br>\n" +
                        "<b>NotNull</b> - Optional. Set to TRUE if this value is required<br>\n" +
                        "<b>Description</b> - Optional. Description of the field</p>");
                vPanel.add(html);

                schemaTsv = new BoundTextArea();
                schemaTsv.setCharacterWidth(80);
                schemaTsv.setHeight("300px");
                schemaTsv.setName("tsv");
                DOM.setElementAttribute(schemaTsv.getElement(), "id", "schemaImportBox");
                vPanel.add(schemaTsv);

                HorizontalPanel buttonPanel = new HorizontalPanel();
                buttonPanel.setSpacing(5);
                buttonPanel.add(new ImageButton("Import", new ClickListener()
                {
                    public void onClick(Widget sender)
                    {
                        processTsv();
                    }
                }));
                buttonPanel.add(new ImageButton("Cancel", new ClickListener()
                {
                    public void onClick(Widget sender)
                    {
                        TsvPopup.this.hide();
                    }
                }));
                vPanel.add(buttonPanel);

                HorizontalPanel mainPanel = new HorizontalPanel();
                mainPanel.setSpacing(10);
                mainPanel.add(vPanel);

                setWidget(mainPanel);
            }

            private void processTsv()
            {
                String tsv = StringUtils.trimToNull(schemaTsv.getText());
                if (tsv == null)
                {
                    Window.alert("Please enter some tab-delimited text");
                    return;
                }
                GWTTabLoader loader = new GWTTabLoader(schemaTsv.getText());
                Map[] data = loader.getData();

                if (data.length == 0)
                {
                    Window.alert("Unable to parse the tab-delimited text");
                    return;
                }

                System.out.println(data);

                // Insert the new properties
                List properties = new ArrayList();
                for (int i=0;i<data.length;i++)
                {
                    GWTPropertyDescriptor prop = new GWTPropertyDescriptor();
                    prop.setName((String)data[i].get("property"));
                    prop.setLabel((String)data[i].get("label"));
                    prop.setDescription((String)data[i].get("description"));
                    prop.setRequired(isRequired(data[i]));
                    prop.setRangeURI(getRangeURI(data[i]));
                    prop.setFormat((String)data[i].get("format"));
                    properties.add(prop);
                }
                _propEdit.setPropertyDescriptors(properties);

                // done, hide ourselves
                TsvPopup.this.hide();
            }

            private boolean isRequired(Map map)
            {
                String reqString = (String)map.get("notnull");
                return reqString != null && reqString.equalsIgnoreCase("TRUE");
            }

            private String getRangeURI(Map map)
            {
                String rangeString = (String)map.get("rangeuri");
                if (rangeString != null)
                {
                    if (rangeString.equalsIgnoreCase("xsd:int"))
                        return TypePicker.xsdInt;
                    if (rangeString.equalsIgnoreCase("xsd:double"))
                        return TypePicker.xsdDouble;
                    if (rangeString.equalsIgnoreCase("xsd:boolean"))
                        return TypePicker.xsdBoolean;
                    if (rangeString.equalsIgnoreCase("xsd:dateTime"))
                        return TypePicker.xsdDateTime;
                }

                // Default to string
                return TypePicker.xsdString;
            }
        }

        public void validate(List errors)
        {
            List error = _propEdit.validate();
            if (error != null)
                errors.addAll(error);
        }

    }

}
