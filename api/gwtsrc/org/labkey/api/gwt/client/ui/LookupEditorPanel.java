/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

package org.labkey.api.gwt.client.ui;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Label;
import com.sencha.gxt.cell.core.client.form.ComboBoxCell;
import com.sencha.gxt.data.shared.ListStore;
import com.sencha.gxt.data.shared.ModelKeyProvider;
import com.sencha.gxt.widget.core.client.container.VerticalLayoutContainer;
import com.sencha.gxt.widget.core.client.form.SimpleComboBox;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.PropertyUtil;

import java.util.List;
import java.util.TreeSet;

/**
 * User: jeckels
 * Date: Sep 24, 2007
 *
 *  LookupEditorPanel for editing lookup information
 */
public class LookupEditorPanel extends VerticalLayoutContainer
{
    private final LookupServiceAsync _service;
    private final String _currentFolderText = "[current " + (PropertyUtil.isProject() ? "project" : "folder") + "]"; // Fix confusion from #19961

    private PropertyType _currentType = null;
    private _ComboBox _comboContainer;
    private _ComboBox _comboSchema;
    private _ComboBox _comboTableName;
    // _comboTableName only captures the table name
    // as a side effect of combo change, stash property type here
    private String _typeURI;

    public LookupEditorPanel(LookupServiceAsync service, GWTPropertyDescriptor initialValue, boolean showContainer)
    {
        if (!(service instanceof CachingLookupService))
            service = new CachingLookupService(service);
        _service = service;
        initUI(showContainer);
        setValue(initialValue);
    }

    @Override
    public void setEnabled(boolean enabled)
    {
        super.setEnabled(enabled);
    }

    public void setCurrentType(PropertyType type)
    {
        _currentType = type;
    }


    protected void initUI(boolean showContainer)
    {
        if (showContainer)
        {
            _comboContainer = new _ComboBox();
            _comboContainer.setAutoSizeList(true);
            _comboContainer.setName("lookupContainer");
            _comboContainer.setEmptyText(_currentFolderText);
            _comboContainer.getElement().setId("lookupFolder");

            populateContainerStore(_comboContainer);

            this.add(new Label("Folder"));
            this.add(_comboContainer);
        }

        // SchemaName
        {
            _comboSchema = new _ComboBox();
            _comboSchema.setName("schema");
            _comboSchema.setEmptyText("Choose schema");
            _comboSchema.getElement().setId("lookupSchema");

            this.add(new Label("Schema"));
            this.add(_comboSchema);
        }

        // TableName
        {
            _comboTableName = new _ComboBox();
            _comboTableName.setName("table");
            _comboTableName.getElement().setId("lookupTable");

            this.add(new Label("Table"));
            this.add(_comboTableName); // this.add(new Label("help")); 
        }
    }


    protected void updateUI()
    {
        String folder = getContainer();
        String schema = getSchemaName();

        //Issue 15485: prevent query if the user enters an invalid container
        if (folder != null && !"".equals(folder) && _comboContainer.getStore().size() > 0 && _comboContainer.getStore().findModelWithKey(folder) == null)
        {
            Window.alert("Container not found: " + folder);
            _comboContainer.reset();
            return;
        }

        populateSchemaStore(_comboSchema, folder);
        if (!_empty(schema))
            populateTableStore(_comboTableName, folder, schema);

        _comboTableName.updateTestMarker();

        updateEmptyText();
    }


    protected void updateEmptyText()
    {
        _comboSchema.setEmptyText("Choose a schema");
        if (_empty(getSchemaName()))
        {
            _comboTableName.setEnabled(false);
            _comboTableName.setEmptyText("Must select a schema");
        }
        else
        {
            _comboTableName.setEnabled(this.isEnabled());
            _comboTableName.setEmptyText("Choose a table");
        }
    }


    public static class ComboModelData
    {
        private String _value;
        private String _text;
        private String _type;

        public ComboModelData(String value)
        {
            this(value,value);
        }
        public ComboModelData(String value, String text)
        {
            super();
            _value = value;
            _text = text == null ? "" : text;
        }
        public ComboModelData(String value, String text, String rangeURI)
        {
            super();
            _value = value;
            _text = text == null ? "" : text;
            _type = rangeURI == null ? "" : rangeURI;
        }

        public String getValue()
        {
            return _value;
        }

        public String getText()
        {
            return _text;
        }

        public String getType()
        {
            return _type;
        }
    }

    void populateContainerStore(_ComboBox comboBox)
    {
        _service.getContainers(new ErrorDialogAsyncCallback<List<String>>()
        {
            public void onSuccess(List<String> l)
            {
                comboBox.add(new ComboModelData("", _currentFolderText));
                for (String folder : l)
                    comboBox.add(new ComboModelData(folder));
            }
        });
    }


    String lastFolderSchemaStore = null;
    
    void populateSchemaStore(_ComboBox comboBox, String f)
    {
        final String folder = null==f ? "" : f;
        if (folder.equals(lastFolderSchemaStore))
            return;
        lastFolderSchemaStore = folder;

        comboBox.getStore().clear();

        _service.getSchemas(folder, getDefaultLookupSchemaName(), new ErrorDialogAsyncCallback<List<String>>()
        {
            public void onSuccess(List<String> l)
            {
                if (!folder.equals(lastFolderSchemaStore) )
                    return;
                comboBox.add(new ComboModelData(""));
                for (String schema : l)
                    comboBox.add(new ComboModelData(schema));

                _comboContainer.updateTestMarker();
            }
        });
    }

    String lastFolderTableStore = null;
    String lastSchemaTableStore = null;
    PropertyType lastKeyType = null;
    
    void populateTableStore(_ComboBox comboBox, String f, final String schema)
    {
        final String folder = null==f ? "" : f;
        if (null == schema) throw new IllegalArgumentException();

        // If it's the same target folder and schema, and the same key as last time, we don't need to requery
        if (folder.equals(lastFolderTableStore) && schema.equals(lastSchemaTableStore) && PropertyUtil.nullSafeEquals(_currentType, lastKeyType))
        {
            // Issue 15772: if this is called before the store loads, we clear the combo inappropriately
            //this check has been commented out, but should be revisited
            //checkForMissingTargetQuery();
            return;
        }

        lastFolderTableStore = folder;
        lastSchemaTableStore = schema;
        lastKeyType = _currentType;

        comboBox.getStore().clear();

        _comboTableName.setEmptyText("Loading tables...");
        _service.getTablesForLookup(folder, schema, new ErrorDialogAsyncCallback<List<LookupService.LookupTable>>()
        {
            public void onSuccess(List<LookupService.LookupTable> list)
            {
                if (!folder.equals(lastFolderTableStore) || !schema.equals(lastSchemaTableStore))
                    return;
                TreeSet<LookupService.LookupTable> set = new TreeSet<LookupService.LookupTable>(list);
                for (LookupService.LookupTable lk : set)
                {
                    String table = lk.table;
                    GWTPropertyDescriptor _pd = lk.key;
                    PropertyType tableKeyType = PropertyType.fromName( _pd.getRangeURI());
                    // Allow it as an option if we haven't already set our field's type, or they match exactly,
                    // or it's a mix of string and multi-line strings

                    if (ConceptPicker.validateLookup(_currentType,schema,table,tableKeyType))
                    {
                        comboBox.add(new ComboModelData(table, table + " (" +  tableKeyType.getShortName() + ")", tableKeyType.getURI()));
                    }
                }

                checkForMissingTargetQuery();

                if (comboBox.getStore().size()==0)
                    _comboTableName.setEmptyText(list.isEmpty()?"No tables found":"No matching tables found");
                else
                    updateEmptyText();

                _comboSchema.updateTestMarker();
            }
        });
    }


    private void checkForMissingTargetQuery()
    {
        boolean matchExisting = false;
        StringBuilder possibleTargets = new StringBuilder();
        String separator = "";
        for (ComboModelData comboModelData : _comboTableName.getStore().getAll())
        {
            String value = comboModelData.getValue();
            possibleTargets.append(separator);
            possibleTargets.append(value);
            separator = "; ";
            if (value.equalsIgnoreCase(_comboTableName.getStringValue()))
            {
                matchExisting = true;
            }
        }
        _log("matchExisting="+matchExisting + " for query " + _comboTableName.getStringValue() + ", possible targets: " + possibleTargets.toString());
        if (!matchExisting)
        {
            _comboTableName.setStringValue(null);
        }
    }


    public void init(GWTPropertyDescriptor pd)
    {
        _log("LookupEditorPanel::init " + pd.getName() + "(" + pd.getRangeURI() + ")");
        setValue(pd);
    }


    public void setContainer(String c)
    {
        _comboContainer.setStringValue(c);
    }


    public String getContainer()
    {
        String c = _comboContainer.getStringValue();
        if (".".equals(c))
            c = null;
        if (_currentFolderText.equals(c))
            c = null;
        return c;
    }

    public void setSchemaName(String name)
    {
        _comboSchema.setStringValue(name);
    }

    public String getSchemaName()
    {
        return _comboSchema.getStringValue();
    }

    public void setTableName(String name)
    {
        _comboTableName.setStringValue(name);
    }

    public String getTableName()
    {
        return _comboTableName.getStringValue();
    }

    public String getTypeURI()
    {
        return _typeURI;
    }

    private class _ComboBox extends SimpleComboBox<ComboModelData>
    {
        boolean autoSizeList = false;
        String _previousSelection = null;
        
        _ComboBox()
        {
            super(item -> item.getText());
            setWidth(250);
            sinkEvents(Event.ONCHANGE);
            setTriggerAction(ComboBoxCell.TriggerAction.ALL);
            setStore(new ListStore<>(new ModelKeyProvider<ComboModelData>(){
                @Override
                public String getKey(ComboModelData item)
                {
                    return item.getValue();
                }
            }));

            addSelectionHandler(event -> {
                ComboModelData item = event.getSelectedItem();
                _log("Change " + String.valueOf(item.getValue()));
                if (null != item.getValue())
                {
                    String type = item.getType();
                    if (null != type)
                        _typeURI = type;
                    _log("  value=" + item.getValue());
                    _log("  text=" + item.getText());
                    _log("  type=" + item.getType());

                    setValue(item);
                }
                _widgetChange();
            });
            updateTestMarker();
        }

        void setAutoSizeList(boolean b)
        {
            autoSizeList = b;
        }

        void clearTestMarker()
        {
            removeStyleName("test-marker-" + _previousSelection);
        }

        void updateTestMarker()
        {
            clearTestMarker();
            _previousSelection = getStringValue();
            addStyleName("test-marker-" + _previousSelection);
        }

        private void setPreviousSelection(String value)
        {
            clearTestMarker();
            _previousSelection = value;
        }

        void setStringValue(String value)
        {
            setValue(new ComboModelData(value));
            updateTestMarker();
        }
        

        String getStringValue()
        {
            ComboModelData data = getValue();
            return null == data ? null : data.getValue();
        }

        void _widgetChange()
        {
            _log("widgetChange(" + getName() + ") = " + getStringValue());
            setPreviousSelection(getStringValue());
            updateUI();
            LookupEditorPanel.this.fireChange();
        }
    }

    public void setValue(GWTPropertyDescriptor pd)
    {
        if (null==pd)
            pd = new GWTPropertyDescriptor();
        
        _comboContainer.setStringValue(pd.getLookupContainer());
        if (_empty(pd.getLookupSchema()) && _empty(pd.getLookupQuery()))
        {
            _comboSchema.setStringValue(getDefaultLookupSchemaName());
            _comboTableName.setStringValue("");
        }
        else
        {
            _comboSchema.setStringValue(pd.getLookupSchema());
            _comboTableName.setStringValue(pd.getLookupQuery());
        }
        updateUI();
    }

    private String getDefaultLookupSchemaName()
    {
        // If "schemaName" property is set (e.g., MetadataQueryAction/MetadataEditor) then use it as the default
        // lookup schema; if not, default to lists.
        String lookupSchemaName = PropertyUtil.getServerProperty("schemaName");
        return (null != lookupSchemaName ? lookupSchemaName : "lists");
    }


    // is this a valid folder/schema/table/range combination
    public boolean isValid()
    {
        if (_empty(getSchemaName()) || _empty(getTableName()))
            return false;
        return true;
    }


    HandlerManager changes = new HandlerManager(this);

    void addChangeHandler(ChangeHandler h)
    {
        changes.addHandler(ChangeEvent.getType(), h);
    }

    void fireChange()
    {
        changes.fireEvent(new ChangeEvent(){});
    }

    boolean _empty(String a) { return null == a || "".equals(a); }
    boolean _eq(String a, String b) { return _empty(a)?_empty(b):a.equals(b); }
    static void _log(String s) {PropertiesEditor._log(s);}
}
