/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

import com.extjs.gxt.ui.client.core.XTemplate;
import com.extjs.gxt.ui.client.data.BaseModelData;
import com.extjs.gxt.ui.client.event.ComponentEvent;
import com.extjs.gxt.ui.client.event.Events;
import com.extjs.gxt.ui.client.event.FieldEvent;
import com.extjs.gxt.ui.client.event.Listener;
import com.extjs.gxt.ui.client.store.ListStore;
import com.extjs.gxt.ui.client.widget.LayoutContainer;
import com.extjs.gxt.ui.client.widget.ListView;
import com.extjs.gxt.ui.client.widget.form.ComboBox;
import com.extjs.gxt.ui.client.widget.layout.TableLayout;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
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
public class LookupEditorPanel extends LayoutContainer
{
    private final LookupServiceAsync _service;

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
        updateUI();
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
        setLayout(new TableLayout(2));
        
        if (showContainer)
        {
            ComboStore store = getContainerStore();
            _comboContainer = new _ComboBox();
            _comboContainer.setAutoSizeList(true);
            _comboContainer.setName("lookupContainer");
            _comboContainer.setEmptyText("Folder");
            _comboContainer.setStore(store);
            _comboContainer.getElement().setId("lookupFolder");

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
        if (folder != null && !"".equals(folder) && _comboContainer.getStore().getCount() > 0 && _comboContainer.getStore().findModel("text", folder) == null)
        {
            Window.alert("Container not found: " + folder);
            _comboContainer.reset();
            return;
        }

        ComboStore schemaStore = (ComboStore)_comboSchema.getStore();
        populateSchemaStore(schemaStore, folder);

        ComboStore tableStore = (ComboStore)_comboTableName.getStore();
        if (!_empty(schema))
            populateTableStore(tableStore,folder, schema);

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


    public static class ComboModelData extends BaseModelData
    {
        public ComboModelData(String value)
        {
            this(value,value);
        }
        public ComboModelData(String value, String text)
        {
            super();
            set("value", value);
            set("text", null == text ? "" : text);
        }
        public ComboModelData(String value, String text, String rangeURI)
        {
            super();
            set("value", value);
            set("text", null==text?"":text);
            set("type", null==rangeURI?"":rangeURI);
        }
    }


    public static class ComboStore extends ListStore<ComboModelData>
    {
    }


    ComboStore getContainerStore()
    {
        final ComboStore ret = new ComboStore();
        _service.getContainers(new ErrorDialogAsyncCallback<List<String>>()
        {
            public void onSuccess(List<String> l)
            {
                ret.add(new ComboModelData("", PropertiesEditor.currentFolder));
                for (String folder : l)
                    ret.add(new ComboModelData(folder));
            }
        });
        return ret;
    }


    String lastFolderSchemaStore = null;
    
    void populateSchemaStore(final ComboStore store, String f)
    {
        final String folder = null==f ? "" : f;
        if (folder.equals(lastFolderSchemaStore))
            return;
        lastFolderSchemaStore = folder;

        store.removeAll();
        
        _service.getSchemas(folder, new ErrorDialogAsyncCallback<List<String>>()
        {
            public void onSuccess(List<String> l)
            {
                if (!folder.equals(lastFolderSchemaStore) )
                    return;
                store.add(new ComboModelData(""));
                for (String schema : l)
                    store.add(new ComboModelData(schema));

                _comboContainer.updateTestMarker();
            }
        });
    }


    String lastFolderTableStore = null;
    String lastSchemaTableStore = null;
    PropertyType lastKeyType = null;
    
    void populateTableStore(final ComboStore store, String f, final String schema)
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

        store.removeAll();

        _comboTableName.setEmptyText("Loading tables...");
        _service.getTablesForLookup(folder, schema, new ErrorDialogAsyncCallback<List<LookupService.LookupTable>>()
        {
            public void onSuccess(List<LookupService.LookupTable> list)
            {
                if (!folder.equals(lastFolderTableStore) || !schema.equals(lastSchemaTableStore))
                    return;
                store.removeAll();
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
                        store.add(new ComboModelData(table, table + " (" +  tableKeyType.getShortName() + ")", tableKeyType.getURI()));
                    }
                }

                checkForMissingTargetQuery();

                if (store.getCount()==0)
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
        for (ComboModelData comboModelData : _comboTableName.getStore().getModels())
        {
            if (comboModelData.<String>get("value").equalsIgnoreCase(_comboTableName.getStringValue()))
            {
                matchExisting = true;
            }
        }
        _log("matchExisting="+matchExisting);
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
        if (PropertiesEditor.currentFolder.equals(c))
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

    private class _ComboBox extends ComboBox<ComboModelData>
    {
        boolean autoSizeList = false;
        String _previousSelection = null;
        
        _ComboBox()
        {
            super();
            setWidth(250);
            sinkEvents(Event.ONCHANGE);
//            setForceSelection(true);
            setTriggerAction(TriggerAction.ALL);
            setStore(new ComboStore());
            addListener(Events.Change,new Listener<FieldEvent>(){
                public void handleEvent(FieldEvent fe)
                {
                    _log("Change " + String.valueOf(fe.getValue()));
                    if (null != fe.getValue())
                    {
                        ComboModelData cmd = (ComboModelData)fe.getValue();
                        String type = cmd.get("type");
                        if (null != type)
                            _typeURI = type;
                        _log("  value="+cmd.get("value"));
                        _log("  text="+cmd.get("text"));
                        _log("  type="+cmd.get("type"));
                    }
                    _widgetChange();
                }
            });
            addListener(Events.Select,new Listener<FieldEvent>(){
                public void handleEvent(FieldEvent fe)
                {
                    _log("Select " + String.valueOf(fe.getValue()));
                    if (null != fe.getValue())
                    {
                        ComboModelData cmd = (ComboModelData)fe.getValue();
                        _log("  value="+cmd.get("value"));
                        _log("  text="+cmd.get("text"));
                        _log("  type="+cmd.get("type"));
                    }
                    _widgetChange();
                }
            });
            addListener(Events.Expand,new Listener<FieldEvent>(){
                public void handleEvent(FieldEvent fe)
                {
                    _listExpand();
                }
            });
//            setTemplate("<tpl for=\".\"><div class=\"x-combo-list-item\" title=\"{" + getDisplayField() + "}\">{" + getDisplayField() + "}</div></tpl>");
            setTemplate(XTemplate.create("<tpl for=\".\"><div class=\"x-combo-list-item\" title=\"{[fm.htmlEncode(values." + getDisplayField() + ")]}\">{[fm.htmlEncode(values." + getDisplayField() + ")]}</div></tpl>"));
            updateTestMarker();
        }

        void setAutoSizeList(boolean b)
        {
            autoSizeList = b;
        }

        @Override
        protected void initList()
        {
            super.initList();
            _listExpand();
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
            return null==getValue()?null:(String)getValue().get("value");
        }


        void _listExpand()
        {
            ListView listView = getListView();
            if (null != listView)
            {
                if (autoSizeList)
                {
                    listView.setAutoWidth(true);
                    ((LayoutContainer)listView.getParent()).setAutoWidth(true);
                }
            }
        }
        
        void _widgetChange()
        {
            _log("widgetChange(" + getName() + ") = " + getStringValue());
            setPreviousSelection(getStringValue());
            updateUI();
            LookupEditorPanel.this.fireChange();
        }


        public void onComponentEvent(ComponentEvent ce)
        {
            super.onComponentEvent(ce);
            if (ce.getEventTypeInt() == Event.ONCHANGE)
            {
                _log("_ComboBox.ONCHANGE()");
                //onChange(ce);
                value = new ComboModelData(getRawValue(),getRawValue());
                _widgetChange();
            }
        }

        // TODO avoid double fireChangeEvent() on blur
        protected void onChange(ComponentEvent be)
        {
            ComboModelData v = getValue();
            value = v;
            fireChangeEvent(focusValue, v);
        }
    }


//    GWTPropertyDescriptor initialValue;
//    GWTPropertyDescriptor value;

    public void setValue(GWTPropertyDescriptor pd)
    {
        if (null==pd)
            pd = new GWTPropertyDescriptor();
        
        _comboContainer.setStringValue(pd.getLookupContainer());
        if (_empty(pd.getLookupSchema()) && _empty(pd.getLookupQuery()))
            _comboSchema.setStringValue("lists");
        else
            _comboSchema.setStringValue(pd.getLookupSchema());
        _comboTableName.setStringValue(pd.getLookupQuery());
        updateUI();
    }

//    public GWTPropertyDescriptor getValue()
//    {
//        // selenium onChange events are not firing
//        _comboContainer.onChange();
//        _comboSchema.onChange();
//        _comboTableName.onChange();
//
//        if (null == value)
//            return null;
//        return copy(value);
//    }


//    public boolean isDirty()
//    {
//        GWTPropertyDescriptor init = null==initialValue ? new GWTPropertyDescriptor() : initialValue;
//        return !_eq(initialValue.getLookupContainer(), value.getLookupContainer()) ||
//               !_eq(initialValue.getLookupSchema(), value.getLookupSchema()) ||
//               !_eq(initialValue.getLookupQuery(), value.getLookupQuery());
//    }


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


//    static GWTPropertyDescriptor copy(GWTPropertyDescriptor src)
//    {
//        GWTPropertyDescriptor ret = new GWTPropertyDescriptor();
//        ret.setRangeURI(src.getRangeURI());
//        ret.setLookupContainer(src.getLookupContainer());
//        ret.setLookupSchema(src.getLookupSchema());
//        ret.setLookupQuery(src.getLookupQuery());
//        return ret;
//    }

    boolean _empty(String a) { return null == a || "".equals(a); }
    boolean _eq(String a, String b) { return _empty(a)?_empty(b):a.equals(b); }
    static void _log(String s) {PropertiesEditor._log(s);}
}
