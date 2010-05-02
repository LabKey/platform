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
import com.extjs.gxt.ui.client.widget.Container;
import com.extjs.gxt.ui.client.event.ComponentEvent;
import com.extjs.gxt.ui.client.store.ListStore;
import com.extjs.gxt.ui.client.widget.LayoutContainer;
import com.extjs.gxt.ui.client.widget.form.ComboBox;
import com.extjs.gxt.ui.client.widget.layout.LayoutData;
import com.extjs.gxt.ui.client.widget.layout.TableData;
import com.extjs.gxt.ui.client.widget.layout.TableLayout;
import com.extjs.gxt.ui.client.widget.table.Table;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.util.StringProperty;
import org.labkey.api.gwt.client.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * User: jeckels
* Date: Sep 24, 2007
*/ // LookupEditor is a popup panel for editing lookup information
public class LookupEditorPanel<FieldType extends GWTPropertyDescriptor> extends LayoutContainer
{
    private StringProperty _container = new StringProperty();
    private StringProperty _schemaName = new StringProperty();
    private StringProperty _tableName = new StringProperty();

    private final LookupServiceAsync _service;
    private _ComboBox _comboContainer;
    private _ComboBox _comboSchema;
    private _ComboBox _comboTableName;

    public LookupEditorPanel(LookupServiceAsync service, boolean showContainer)
    {
        setLayout(new TableLayout(2));
        
        _service = service;
        int row = 0;

        if (showContainer)
        {
            ComboStore store = getContainerStore();
            _comboContainer = new _ComboBox();
            _comboContainer.setStore(store);
            _comboContainer.getElement().setId("lookupFolder");

            this.add(new Label("Folder"));
            this.add(_comboContainer);
            row++;
        }

        // SchemaName
        {
            _comboSchema = new _ComboBox()
            {
                @Override
                protected void onTriggerClick(ComponentEvent ce)
                {
                    //populateSchemaStore(_comboSchema.getStore(), getContainer());
                    super.onTriggerClick(ce);
                }
            };
            _comboSchema.getElement().setId("lookupSchema");

            this.add(new Label("Schema"));
            this.add(_comboSchema);
            row++;
        }

        // TableName
        {
            _comboTableName = new _ComboBox();
            _comboTableName.getElement().setId("lookupTable");

            this.add(new Label("Table"));
            this.add(_comboTableName); // this.add(new Label("help")); 
            row++;
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
            set("text", text);
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
                ret.add(new ComboModelData(PropertiesEditor.currentFolder, ""));
                for (String folder : l)
                    ret.add(new ComboModelData(folder));
            }
        });
        return ret;
    }


    String lastFolder = null;
    
    void populateSchemaStore(final ComboStore store, String folder)
    {
        if (lastFolder != null && lastFolder.equals(folder) && !store.getModels().isEmpty())
            return;

        store.removeAll();
        
        _service.getSchemas(folder, new AsyncCallback<List<String>>()
        {
            public void onFailure(Throwable caught)
            {
                Window.alert(caught.getMessage());
            }

            public void onSuccess(List<String> l)
            {
                store.add(new ComboModelData(""));
                for (String schema : l)
                    store.add(new ComboModelData(schema));
            }
        });
    }


    void populateTableStore(final ComboStore store, final String folder, final String schema)
    {
        if (null == schema)
        {
            Window.alert("Please select a schema");
            return;
        }
        store.removeAll();
        
        _service.getTablesForLookup(folder, schema, new AsyncCallback<Map<String, String>>()
        {

            public void onFailure(Throwable caught)
            {
                Window.alert(caught.getMessage());
            }

            public void onSuccess(Map<String, String> m)
            {
                if (m == null)
                {
                    Window.alert("Could not find any tables in the '" + schema + "' schema in the selected folder.");
                    return;
                }
                for (String table : m.keySet())
                {
                    String key = m.get(table);
                    store.add(new ComboModelData(table, table + " (" + key + ")"));
                }
            }
        });
    }


    public void init(FieldType pd)
    {
        String container = pd.getLookupContainer();
        if (null == StringUtils.trimToNull(container))
            container = "";
        setContainer(container);
        String schema = StringUtils.trimToNull(pd.getLookupSchema());
        if (null == schema)
            schema = "lists";
        setSchemaName(schema);
        setTableName(pd.getLookupQuery());

        // since we're reusing these we have to ping them
        if (_comboContainer != null)
        {
            _comboContainer.setStringValue(StringUtils.nullToEmpty(container));
        }
        _comboSchema.setStringValue(StringUtils.nullToEmpty(schema));
        _comboTableName.setStringValue(StringUtils.nullToEmpty(pd.getLookupQuery()));
    }


    public void setContainer(String c)
    {
        _container.set(c);
    }

    public String getContainer()
    {
        String c = StringUtils.trimToNull(_container.getString());
        if (".".equals(c))
            c = null;
        if (PropertiesEditor.currentFolder.equals(c))
            c = null;
        return c;
    }

    public void setSchemaName(String name)
    {
        _schemaName.set(name);
    }

    public String getSchemaName()
    {
        if ("".equals(_schemaName.getString()))
        {
            return null;
        }
        return _schemaName.getString();
    }

    public void setTableName(String name)
    {
        _tableName.set(name);
    }

    public String getTableName()
    {
        if ("".equals(_tableName.getString()))
        {
            return null;
        }
        return _tableName.getString();
    }

    private static class _ComboBox extends ComboBox
    {
        _ComboBox()
        {
            super();
            setStore(new ComboStore());
        }

        void setStringValue(String value)
        {
            setValue(new ComboModelData(value));
        }
    }
}