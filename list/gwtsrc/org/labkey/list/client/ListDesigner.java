/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

package org.labkey.list.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.WindowCloseListener;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Node;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.*;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 24, 2007
 * Time: 9:24:04 AM
 */
public class ListDesigner implements EntryPoint, Saveable<GWTList>
{
    private String _returnURL;
    private String _cancelURL;

    private GWTList _list;
    private GWTDomain _domain;

    // UI bits
    private RootPanel _root = null;
    private FlexTable _buttons = null;
    private Label _loading = null;
    private PropertiesEditor _propTable = null;
    boolean _saved = false;

    private ListPropertiesPanel _propertiesPanel;
    private ListSchema _schemaPanel;
    private boolean _dirty;

    private SubmitButton _saveButton;

    private class DomainListSaveable implements Saveable<GWTDomain>
    {
        private Saveable<GWTList> _listSaveable;

        public DomainListSaveable(Saveable<GWTList> listSaveable)
        {
            _listSaveable = listSaveable;
        }

        public void save()
        {
            _listSaveable.save();
        }

        public void save(final SaveListener<GWTDomain> gwtDomainSaveListener)
        {
            // okay, this gets a bit mind bending.  When asked to save, the GWTDomain saveable delegates
            // to the GWTList Saveable.  When the list reply comes back, the GWTList Saveable must
            // make an async call to get the GWT Domain so we can forward the successful save message back
            // to the original GWT Domain saveable.
            _listSaveable.save(new SaveListener<GWTList>()
            {
                public void saveSuccessful(GWTList listResult, String designerUrl)
                {
                    asyncGetDefinition(gwtDomainSaveListener);
                }
            });
        }

        public void cancel()
        {
            _listSaveable.cancel();
        }

        public void finish()
        {
            _listSaveable.finish();
        }

        public boolean isDirty()
        {
            return _listSaveable.isDirty();
        }
    }


    public void onModuleLoad()
    {
        int listId = Integer.parseInt(PropertyUtil.getServerProperty("listId"));
        _returnURL = PropertyUtil.getServerProperty("returnURL");
        _cancelURL = PropertyUtil.getServerProperty("cancelURL");

        _root = RootPanel.get("org.labkey.list.Designer-Root");

        _loading = new Label("Loading...");

        _propTable = new PropertiesEditor(new DomainListSaveable(this), getService());

        _buttons = new FlexTable();

        _saveButton = new SubmitButton();

        _buttons.setWidget(0, 0, _saveButton);
        _buttons.setWidget(0, 1, new CancelButton());

        _root.add(_loading);


        // NOTE for now we're displaying list info w/ static HTML
        asyncGetList(listId);

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


    public void setList(GWTList ds)
    {
        _list = ds;
        asyncGetDefinition();
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
        if (null != _domain && null != _list)
        {
            _root.remove(_loading);
            _root.add(_buttons);

            _propertiesPanel = new ListPropertiesPanel();
            _root.add(new WebPartPanel("List Properties", _propertiesPanel));

            _schemaPanel = new ListSchema(_propTable);
            _root.add(new WebPartPanel("List Fields", _schemaPanel));
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

    public void save(final SaveListener<GWTList> listener)
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

        AsyncCallback<List<String>> callback = new AsyncCallback<List<String>>() {
            public void onFailure(Throwable caught)
            {
                Window.alert(caught.getMessage());
                _saveButton.setEnabled(true);
            }

            public void onSuccess(List<String> errors)
            {
                if (null == errors || errors.isEmpty())
                {
                    _saved = true;  // avoid popup warning
                    if (listener != null)
                        listener.saveSuccessful(_list, PropertyUtil.getCurrentURL());
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

        getService().updateListDefinition(_list, _domain, _propTable.getUpdates(), callback);
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
        save(new SaveListener<GWTList>()
        {
            public void saveSuccessful(GWTList list, String designerUrl)
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

    private ListEditorServiceAsync _service = null;
    private ListEditorServiceAsync getService()
    {
        if (_service == null)
        {
            _service = (ListEditorServiceAsync) GWT.create(ListEditorService.class);
            ServiceUtil.configureEndpoint(_service, "listEditorService");
        }
        return _service;
    }


    void asyncGetList(int id)
    {
        getService().getList(id, new AsyncCallback<GWTList>()
        {
                public void onFailure(Throwable caught)
                {
                    Window.alert(caught.getMessage());
                    _loading.setText("ERROR: " + caught.getMessage());
                }

                public void onSuccess(GWTList result)
                {
                    setList(result);
                }
        });
    }

    void asyncGetDefinition()
    {
        asyncGetDefinition(null);
    }

    void asyncGetDefinition(final Saveable.SaveListener<GWTDomain> saveListener)
    {
        getService().getDomainDescriptor(_list, new AsyncCallback<GWTDomain>()
        {
                public void onFailure(Throwable caught)
                {
                    Window.alert(caught.getMessage());
                    _loading.setText("ERROR: " + caught.getMessage());
                }

                public void onSuccess(GWTDomain result)
                {
                    GWTDomain domain = result;
                    if (null == domain)
                    {
                        domain = new GWTDomain();
                        Window.alert("Error editing list: " + _list.getName());
                    }

                    setDomain(domain);

                    if (saveListener != null)
                        saveListener.saveSuccessful(domain, PropertyUtil.getCurrentURL());
                }
        });
    }


    private interface WidgetUpdatable
    {
        void update(Widget widget);
    }


    private class ListPropertiesPanel extends VerticalPanel
    {
        final FlexTable _table = new FlexTable();
        RadioButton _noneButton;

        public ListPropertiesPanel()
        {
            super();
            createPanel();
        }

        private void createPanel()
        {
            String labelStyleName="labkey-form-label"; // Pretty yellow background for labels
            HTMLTable.CellFormatter cellFormatter = _table.getCellFormatter();

            int row = 0;

            add(_table);

            // NAME
            {
            Label listNameTextBox = new Label(_list.getName());
            HorizontalPanel panel = new HorizontalPanel();
            panel.add(new Label("Name"));
            //panel.add(new HelpPopup("Name", "Name of this List"));
            _table.setWidget(row, 0, panel);
            cellFormatter.setStyleName(row, 0, labelStyleName);
            _table.setWidget(row, 1, listNameTextBox);
            row++;
            }

            // DESCRIPTION
            {
            BoundTextAreaBox descriptionTextBox = new BoundTextAreaBox("Description", "ff_description", _list.name, null);
            HorizontalPanel panel = new HorizontalPanel();
            panel.add(new Label("Description"));
            //panel.add(new HelpPopup("Name", "Name of this List"));
            _table.setWidget(row, 0, panel);
            cellFormatter.setStyleName(row, 0, labelStyleName);
            _table.setWidget(row, 1, descriptionTextBox);
            row++;
            }


            // TITLE
            {
                BoundListBox titleListBox = new BoundListBox(false, _list.titleField, null);
                HorizontalPanel panel = new HorizontalPanel();
                panel.add(new Label("Title Field"));
                //panel.add(new HelpPopup("Name", "Name of this List"));
                _table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);
                _table.setWidget(row, 1, titleListBox);
                row++;
            }


            // DISCUSSION LINKS


            // ALLOW


            
            /*
            BoundTextBox dsName = new BoundTextBox("dsName", _list.getName(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _list.setName(((TextBox)widget).getText());
                }
            });
            DOM.setElementAttribute(dsName._box.getElement(), "id", "ListDesignerName");
            HorizontalPanel panel = new HorizontalPanel();
            panel.add(new Label("Name"));
            panel.add(new HelpPopup("Name", "Short unique name, e.g. 'DEM1'"));
            _table.setWidget(row, 0, panel);
            cellFormatter.setStyleName(row, 0, labelStyleName);
            _table.setWidget(row, 1, dsName);

            TextBox listName = new TextBox();
            listName.setText(Integer.toString(_list.getName()));
            listName.setEnabled(false);

            _table.setHTML(row, 2, "ID");
            cellFormatter.setStyleName(row, 2, labelStyleName);
            _table.setWidget(row++, 3, listName);

            BoundTextBox dsLabel = new BoundTextBox("dsLabel", _list.getLabel(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _list.setLabel(((TextBox)widget).getText());
                }
            });
            panel = new HorizontalPanel();
            panel.add(new Label("Label"));
            panel.add(new HelpPopup("Label", "Descriptive label, e.g. 'Demographics form 1'"));

            _table.setWidget(row, 0, panel);
            cellFormatter.setStyleName(row, 0, labelStyleName);
            _table.setWidget(row, 1, dsLabel);

            BoundTextBox dsCategory = new BoundTextBox("dsCategory", _list.getCategory(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _list.setCategory(((TextBox)widget).getText());
                }
            });
            panel = new HorizontalPanel();
            panel.add(new Label("Category"));
            panel.add(new HelpPopup("List Category", "Lists with the same category name are shown together in the study navigator and list list."));
            _table.setWidget(row, 2, panel);
            cellFormatter.setStyleName(row, 2, labelStyleName);
            _table.setWidget(row++, 3, dsCategory);

            String selection = null;
            if (_list.getCohortId() != null)
                selection = _list.getCohortId().toString();
            BoundListBox dsCohort = new BoundListBox(_list.getCohortMap(), selection, new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    ListBox lb = (ListBox)widget;
                    if (lb.getSelectedIndex() != -1)
                    {
                        String value = lb.getValue(lb.getSelectedIndex());
                        if (value.length() > 0)
                            _list.setCohortId(Integer.valueOf(value));
                        else
                            _list.setCohortId(null);
                    }
                }
            });
            panel = new HorizontalPanel();
            panel.add(new Label("Cohort Association"));
            _table.setWidget(row, 0, panel);
            cellFormatter.setStyleName(row, 0, labelStyleName);

            if (!_list.getCohortMap().isEmpty())
                _table.setWidget(row, 1, dsCohort);
            else
                _table.setWidget(row, 1, new HTMLPanel("<em>No cohorts defined</em>"));

            if ("VISIT".equals(_timepointType))
            {
                BoundListBox dsVisitDate = new BoundListBox(_list.getVisitDateMap(), _list.getVisitDatePropertyName(), new WidgetUpdatable()
                {
                    public void update(Widget widget)
                    {
                        ListBox lb = (ListBox)widget;
                        if (lb.getSelectedIndex() != -1)
                        {
                            _list.setVisitDatePropertyName(lb.getValue(lb.getSelectedIndex()));
                        }
                    }
                });
                panel = new HorizontalPanel();
                panel.add(new Label("Visit Date Column"));
                panel.add(new HelpPopup("Visit Date Column", "If the official 'Visit Date' for a visit can come from this list, choose the date column to represent it. Note that since lists can include data from many visits, each visit must also indicate the official 'VisitDate' list."));
                _table.setWidget(row, 2, panel);
                cellFormatter.setStyleName(row, 2, labelStyleName);
                _table.setWidget(row++, 3, dsVisitDate);
            }
            else
                row++;

            panel = new HorizontalPanel();
            panel.add(new Label("Additional Key Column"));
            panel.add(new HelpPopup("Additional Key",
                    "If list has more than one row per participant/visit, " +
                            "an additional key field must be provided. There " +
                            "can be at most one row in the list for each " +
                            "combination of participant, visit and key. " +
                            "<ul><li>None: No additional key</li>" +
                            "<li>Data Field: A user-managed key field</li>" +
                            "<li>Managed Field: A numeric field defined below will be managed" +
                            "by the server to make each new entry unique</li>" +
                            "</ul>"));

            cellFormatter.setStyleName(row, 0, labelStyleName);
            _table.setWidget(row, 0, panel);

            VerticalPanel vPanel = new VerticalPanel();
            vPanel.setSpacing(1);
            panel = new HorizontalPanel();
            panel.setSpacing(2);

            _noneButton = new RadioButton("additionalKey", "None");
            _noneButton.setChecked(_list.getKeyPropertyName() == null);
            setCheckboxId(_noneButton.getElement(), "button_none");

            if (fromAssay)
                _noneButton.setEnabled(false);

            panel.add(_noneButton);
            vPanel.add(panel);
            
            panel = new HorizontalPanel();
            panel.setSpacing(2);
            panel.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);
            final RadioButton dataFieldButton = new RadioButton("additionalKey", "Data Field:");
            setCheckboxId(dataFieldButton.getElement(), "button_dataField");
            if (fromAssay)
                dataFieldButton.setEnabled(false);
            dataFieldButton.setChecked(_list.getKeyPropertyName() != null && !_list.getKeyPropertyManaged());
            panel.add(dataFieldButton);

            final BoundListBox dataFieldsBox = new BoundListBox(new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _list.setKeyPropertyManaged(false);
                    _list.setKeyPropertyName(((BoundListBox)widget).getSelectedValue());
                }
            });
            DOM.setElementAttribute(dataFieldsBox.getElement(), "id", "list_dataField");
            dataFieldsBox.setEnabled(!fromAssay && !_list.getKeyPropertyManaged() && _list.getKeyPropertyName() != null);

            panel.add(dataFieldsBox);
            vPanel.add(panel);

            panel = new HorizontalPanel();
            panel.setSpacing(2);
            panel.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);
            final RadioButton managedButton = new RadioButton("additionalKey", "Managed Field:");
            setCheckboxId(managedButton.getElement(), "button_managedField");
            if (fromAssay)
                managedButton.setEnabled(false);
            managedButton.setChecked(_list.getKeyPropertyManaged());
            panel.add(managedButton);

            final BoundListBox managedFieldsBox = new BoundListBox(new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _list.setKeyPropertyManaged(true);
                    _list.setKeyPropertyName(((BoundListBox)widget).getSelectedValue());
                }
            });
            DOM.setElementAttribute(managedFieldsBox.getElement(), "id", "list_managedField");
            managedFieldsBox.setEnabled(_list.getKeyPropertyManaged() && !fromAssay);

            panel.add(managedFieldsBox);
            vPanel.add(panel);
            _table.setWidget(row, 1, vPanel);

            BoundTextArea description = new BoundTextArea(_list.getDescription(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _list.setDescription(((TextArea)widget).getText());
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
                        _list.setKeyPropertyName(null);
                        _list.setKeyPropertyManaged(false);
                        dataFieldsBox.setEnabled(false);
                        managedFieldsBox.setEnabled(false);
                    }
                    else if (sender == dataFieldButton)
                    {
                        _list.setKeyPropertyName(dataFieldsBox.getSelectedValue());
                        _list.setKeyPropertyManaged(false);
                        dataFieldsBox.setEnabled(true);
                        managedFieldsBox.setEnabled(false);

                    }
                    else if (sender == managedButton)
                    {
                        _list.setKeyPropertyName(managedFieldsBox.getSelectedValue());
                        _list.setKeyPropertyManaged(true);
                        dataFieldsBox.setEnabled(false);
                        managedFieldsBox.setEnabled(true);
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


            BoundCheckBox demographicData = new BoundCheckBox("", _list.getDemographicData(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _list.setDemographicData(((CheckBox)widget).isChecked());
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

            BoundCheckBox showByDefault = new BoundCheckBox("", _list.getShowByDefault(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _list.setShowByDefault(((CheckBox)widget).isChecked());
                }
            });
            panel = new HorizontalPanel();
            panel.add(new Label("Show In Overview"));
            panel.add(new HelpPopup("Show In Overview", "When this item is checked, this list will show in the overview grid by default. It can be unhidden by clicking 'Show Hidden Data.'"));
            _table.setWidget(row, 0, panel);
            cellFormatter.setStyleName(row, 0, labelStyleName);
            _table.setWidget(row++, 1, showByDefault);
            */
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
            Map<String,String> numericFields = new HashMap<String,String>();

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

            String keyName = _list.getKeyPropertyName();

        }

        public void validate(List<String> errors)
        {
            if (_list.getName() == null || _list.getName().length() == 0)
                errors.add("List name cannot be empty.");

            if ("".equals(_list.getKeyPropertyName()))
                errors.add("Please select a field name for the key column.");
        }
    }


    private class ListSchema extends FlexTable
    {
        private PropertiesEditor _propEdit;

        public ListSchema(PropertiesEditor propEdit)
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
