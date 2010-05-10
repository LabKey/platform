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

package org.labkey.api.gwt.client.ui;

import com.extjs.gxt.ui.client.data.BaseModelData;
import com.extjs.gxt.ui.client.event.BaseEvent;
import com.extjs.gxt.ui.client.event.ComponentEvent;
import com.extjs.gxt.ui.client.event.Events;
import com.extjs.gxt.ui.client.event.Listener;
import com.extjs.gxt.ui.client.store.ListStore;
import com.extjs.gxt.ui.client.widget.LayoutContainer;
import com.extjs.gxt.ui.client.widget.form.ComboBox;
import com.extjs.gxt.ui.client.widget.form.TextField;
import com.extjs.gxt.ui.client.widget.layout.TableLayout;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;

import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: Sep 24, 2007
 *
 *  LookupEditorPanel for editing lookup information
 */
public class LookupEditorPanel extends LayoutContainer
{
    private final LookupServiceAsync _service;

    private PropertyType _keyType = null;
    private _ComboBox _comboContainer;
    private _ComboBox _comboSchema;
    private _ComboBox _comboTableName;

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

    public void setKeyType(PropertyType type)
    {
        _keyType = type;
    }

    protected void initUI(boolean showContainer)
    {
        setLayout(new TableLayout(2));
        
        if (showContainer)
        {
            ComboStore store = getContainerStore();
            _comboContainer = new _ComboBox();
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

        ComboStore schemaStore = (ComboStore)_comboSchema.getStore();
        populateSchemaStore(schemaStore, folder);

        ComboStore tableStore = (ComboStore)_comboTableName.getStore();
        if (!_empty(schema))
            populateTableStore(tableStore,folder, schema);

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
            set("text", null==text?"":text);
        }
    }


    public static class ComboStore extends ListStore<ComboModelData>
    {
    }


    ComboStore getContainerStore()
    {
        final ComboStore ret = new ComboStore();
        _service.getContainers(new AsyncCallback<List<String>>()
        {
            public void onFailure(Throwable caught)
            {
                Window.alert(caught.getMessage());
            }

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
        
        _service.getSchemas(folder, new AsyncCallback<List<String>>()
        {
            public void onFailure(Throwable caught)
            {
                Window.alert(caught.getMessage());
            }

            public void onSuccess(List<String> l)
            {
                if (!folder.equals(lastFolderSchemaStore) )
                    return;
                store.add(new ComboModelData(""));
                for (String schema : l)
                    store.add(new ComboModelData(schema));
            }
        });
    }


    String lastFolderTableStore = null;
    String lastSchemaTableStore = null;
    
    void populateTableStore(final ComboStore store, String f, final String schema)
    {
        final String folder = null==f ? "" : f;
        if (null == schema) throw new IllegalArgumentException();

        if (folder.equals(lastFolderTableStore) && schema.equals(lastSchemaTableStore))
            return;

        lastFolderTableStore = folder;
        lastSchemaTableStore = schema;

        store.removeAll();
        
        _service.getTablesForLookup(folder, schema, new AsyncCallback<Map<String, GWTPropertyDescriptor>>()
        {

            public void onFailure(Throwable caught)
            {
                Window.alert(caught.getMessage());
            }

            public void onSuccess(Map<String, GWTPropertyDescriptor> m)
            {
                if (!folder.equals(lastFolderTableStore) || !schema.equals(lastSchemaTableStore))
                    return;
                for (String table : m.keySet())
                {
                    GWTPropertyDescriptor _pd = m.get(table);
                    PropertyType tableKeyType = PropertyType.fromName( _pd.getRangeURI());
                    if (null != _keyType)
                    {
                        // CONSIDER: how to you do disabled items in an Ext ComboBox?
                        if (_keyType != tableKeyType)
                            continue;
                    }
                    store.add(new ComboModelData(table, table + " (" +  tableKeyType.getShortName() + ")"));
                }
                if (store.getCount()==0)
                {
                    if (m.size() == 0)
                        Window.alert("Could not find any tables in the '" + schema + "' schema in the selected folder.");
                    else
                        Window.alert("There are no tables available with a matching primary key: " + _keyType.getShortName());
                    return;
                }
                updateEmptyText();
            }
        });
    }


    public void init(GWTPropertyDescriptor pd)
    {
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

//    void resetRangeURI()
//    {
//        value.setRangeURI(null);
//        getRangeURI(null);
//    }
//
//    String getRangeURI()
//    {
//        return value.getRangeURI();
//    }
//
//    void getRangeURI(final AsyncCallback<String> async)
//    {
//        final String container = getContainer();
//        final String schema = getSchemaName();
//        String table = getTableName();
//        if (_empty(schema) || _empty(table))
//        {
//            value.setRangeURI(null);
//            return;
//        }
//        _service.getTablesForLookup(container, schema, new AsyncCallback<Map<String, GWTPropertyDescriptor>>()
//        {
//            public void onFailure(Throwable caught)
//            {
//                if (null != async)
//                    async.onSuccess(null);
//            }
//
//            public void onSuccess(Map<String, GWTPropertyDescriptor> result)
//            {
//                if (!_eq(container,getContainer()) || !_eq(schema,getSchemaName()))
//                    return;
//                GWTPropertyDescriptor pd = result.get(getTableName());
//                if (null != pd)
//                    value.setRangeURI(pd.getRangeURI());
//                if (null != async)
//                    async.onSuccess(value.getRangeURI());
//                if (null != value.getRangeURI())
//                    fireChange();
//            }
//        });
//    }


    private class _ComboBox extends ComboBox
    {
        _ComboBox()
        {
            super();
            sinkEvents(Event.ONCHANGE);
//            setForceSelection(true);
            setTriggerAction(TriggerAction.ALL);
            setStore(new ComboStore());
            addListener(Events.Change,new Listener(){
                public void handleEvent(BaseEvent be)
                {
                    widgetChange();
                }
            });
            addListener(Events.Select,new Listener(){
                public void handleEvent(BaseEvent be)
                {
                    widgetChange();
                }
            });
        }

        void setStringValue(String value)
        {
            setValue(new ComboModelData(value));
        }
        
        String getStringValue()
        {
            return null==getValue()?null:(String)getValue().get("value");
        }

        void widgetChange()
        {
            _log("widgetChange(" + getName() + ") = " + getStringValue());
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
                widgetChange();
            }
        }

        // TODO avoid double fireChangeEvent() on blur
        protected void onChange(ComponentEvent be)
        {
            Object v = getValue();
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