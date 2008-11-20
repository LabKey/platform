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

package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.util.IPropertyWrapper;
import org.labkey.api.gwt.client.util.StringProperty;
import org.labkey.api.gwt.client.util.StringUtils;

import java.util.*;

/**
 * User: jeckels
* Date: Sep 24, 2007
*/ // LookupEditor is a popup panel for editing lookup information
public class LookupEditor<FieldType extends GWTPropertyDescriptor> extends DialogBox
{
    FlexTable _table;

    private StringProperty _container = new StringProperty();
    private StringProperty _schemaName = new StringProperty();
    private StringProperty _tableName = new StringProperty();

    private String _title;

    private TextBox _txtContainer;
    private TextBox _txtSchemaName;
    private TextBox _txtTableName;
    private FieldType _pd;
    private final LookupServiceAsync _service;
    private final LookupListener<FieldType> _listener;

    public LookupEditor(LookupServiceAsync service, LookupListener<FieldType> listener, boolean showContainer)
    {
        _service = service;
        _listener = listener;
        // set up labels, add textboxes when bound
        _table = new FlexTable();
        int row = 0;

//            String contextPath = PropertyUtil.getServerProperty("contextPath")
//            Image image = new Image("X");

        if (showContainer)
        {
            PropertiesEditor.setBoldText(_table, row, 0, "Folder");
            _txtContainer = new TextBox();
            _txtContainer.setMaxLength(250);
            _txtContainer.setWidth("200px");
            DOM.setElementProperty(_txtContainer.getElement(), "id", "folder");
            _table.setWidget(row, 1, _txtContainer);
            Image image = PropertiesEditor.getPopupImage("folder", new ClickListener()
            {
                public void onClick(Widget sender)
                {
                    showContainers(sender.getAbsoluteLeft() - 200, sender.getAbsoluteTop() + 20);
                }
            });
            image.addMouseListener(new Tooltip("Click to choose a target folder"));
            _table.setWidget(row, 2, image);
            row++;
        }

        _txtSchemaName = new TextBox();
        PropertiesEditor.setBoldText(_table, row, 0, "Schema");
        _txtSchemaName.setMaxLength(250);
        _txtSchemaName.setWidth("200px");
        DOM.setElementProperty(_txtSchemaName.getElement(), "id", "schema");
        _table.setWidget(row, 1, _txtSchemaName);
        Image image = PropertiesEditor.getPopupImage("schema", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                showSchemas(sender.getAbsoluteLeft() - 200, sender.getAbsoluteTop() + 20);
            }
        });
        image.addMouseListener(new Tooltip("Click to choose a target schema"));
        _table.setWidget(row, 2, image);
        row++;

        PropertiesEditor.setBoldText(_table, row, 0, "Table");
        _txtTableName = new TextBox();
        _txtTableName.setMaxLength(250);
        _txtTableName.setWidth("200px");
        DOM.setElementProperty(_txtTableName.getElement(), "id", "table");
        _table.setWidget(row, 1, _txtTableName);
        image = PropertiesEditor.getPopupImage("table", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                showTables(sender.getAbsoluteLeft() - 200, sender.getAbsoluteTop() + 20);
            }
        });
        image.addMouseListener(new Tooltip("Click to choose a target table"));
        _table.setWidget(row, 2, image);
        row++;

        FlexTable buttonTable = new FlexTable();
        int buttonIndex = 0;

        ImageButton clear = new ImageButton("Clear", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                if (_txtContainer != null)
                {
                    _txtContainer.setText("");
                }
                _txtSchemaName.setText("");
                _txtTableName.setText("");
                LookupEditor.this.hide();
            }
        });
        buttonTable.setWidget(row, buttonIndex++, clear);

        ImageButton cancel = new ImageButton("Cancel", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                _pd = null;
                LookupEditor.this.hide();
            }
        });
        buttonTable.setWidget(row, buttonIndex++, cancel);

        ImageButton close = new ImageButton("Close", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                LookupEditor.this.hide();
            }
        });
        buttonTable.setWidget(row, buttonIndex++, close);

        _table.setWidget(row, 1, buttonTable);
        _table.getCellFormatter().setHorizontalAlignment(row, 1, HasHorizontalAlignment.ALIGN_RIGHT);
        row++;

        setWidget(_table);
    }


    // cache this
    List<String> _containersText = null;
    List<String> _containersValues  = null;

    void showContainers(final int x, final int y)
    {
        if (null != _containersText)
        {
            showContainersPopup(x, y);
            return;
        }

        // request container list
        _service.getContainers(new AsyncCallback<List<String>>()
        {
            public void onFailure(Throwable caught)
            {
                Window.alert(caught.getMessage());
            }

            public void onSuccess(List<String> l)
            {
                _containersValues = new ArrayList<String>();
                _containersValues.add("");
                _containersValues.addAll(l);
                _containersText = new ArrayList<String>();
                _containersText.add(PropertiesEditor.currentFolder);
                _containersText.addAll(l);
                showContainersPopup(x, y);
            }
        });
    }

    private void showContainersPopup(int x, int y)
    {
        PopupList popupList = new PopupList(_containersText, _containersValues)
        {
            public void hide()
            {
                super.hide();
                setContainer(getValue());
                _txtContainer.setText(StringUtils.trimToEmpty(getValue()));
            }
        };
        popupList.setValue(getContainer());
        popupList.setPopupPosition(x, y);
        popupList.show();
    }


    void showSchemas(final int x, final int y)
    {
        String container = getContainer();
        _service.getSchemas(container, new AsyncCallback<List<String>>()
        {
            public void onFailure(Throwable caught)
            {
                Window.alert(caught.getMessage());
            }

            public void onSuccess(List<String> l)
            {
                l.add(0,"");
                PopupList popupList = new PopupList(l, l)
                {
                    public void hide()
                    {
                        super.hide();
                        setSchemaName(getValue());
                        _txtSchemaName.setText(StringUtils.trimToEmpty(getValue()));
                    }
                };
                popupList.setValue(getSchemaName());
                popupList.setPopupPosition(x, y);
                popupList.show();
            }
        });
    }


    void showTables(final int x, final int y)
    {
        String container = getContainer();
        final String schema = StringUtils.trimToNull(getSchemaName());
        if (null == schema)
        {
            Window.alert("Please select a schema");
            return;
        }
        _service.getTablesForLookup(container, schema, new AsyncCallback<Map<String, String>>()
        {
            public void onFailure(Throwable caught)
            {
                Window.alert(caught.getMessage());
            }

            public void onSuccess(Map<String, String> m)
            {
                List<String> tables = new ArrayList<String>();
                if (m == null)
                {
                    Window.alert("Could not find any tables in the '" + schema + "' schema in the selected folder.");
                    return;
                }
                tables.addAll(m.keySet());
                Collections.sort(tables);
                List<String> display = new ArrayList<String>();
                for (String table : tables)
                    display.add(table + " (" + m.get(table) + ")");
                display.add(0,"");
                tables.add(0,null);
                PopupList popupList = new PopupList(display, tables)
                {
                    public void hide()
                    {
                        super.hide();
                        setTableName(getValue());
                        _txtTableName.setText(StringUtils.trimToEmpty(getValue()));
                    }
                };
                popupList.setValue(getTableName());
                popupList.setPopupPosition(x, y);
                popupList.show();
            }
        });
    }


    public void init(FieldType pd)
    {
        _pd = pd;
        // we could bind directly to the property descriptor
        // I think it's better to only update when all the properties are correct
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
        if (_txtContainer != null)
        {
            _txtContainer.setText(StringUtils.nullToEmpty(container));
        }
        _txtSchemaName.setText(StringUtils.nullToEmpty(schema));
        _txtTableName.setText(StringUtils.nullToEmpty(pd.getLookupQuery()));

        if (_title == null)
        {
            if (null == pd.getName())
                setText("Lookup Properties");
            else
                setText(pd.getName() + " -- Lookup Properties");
        }
        else
        {
            setText(_title);
        }
    }

    public void show()
    {
        super.show();
        _txtTableName.setFocus(true);
    }


    public void hide()
    {
        super.hide();
        if (_pd != null)
        {
            if (_txtContainer != null)
            {
                _container.set(_txtContainer.getText());
                _pd.setLookupContainer(getContainer());
            }
            _schemaName.set(_txtSchemaName.getText());
            _tableName.set(_txtTableName.getText());
            _pd.setLookupSchema(getSchemaName());
            _pd.setLookupQuery(getTableName());
            _listener.lookupUpdated(_pd);
            _pd = null;
        }
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

    private class PopupList extends PopupPanel implements ChangeListener
    {
        IPropertyWrapper _prop;
        ListBox _list = new ListBox();
        String _value = null;

        PopupList(List<String> items, List<String> values)
        {
            super(true);
            if (values == null)
                values = items;

            for (int i=0; i<values.size(); i++)
                _list.addItem(items.get(i), values.get(i));

            _list.addChangeListener(this);
            _list.setVisibleItemCount(10);
            _list.setWidth("200px");
            setWidget(_list);
        }

        void setValue(String v)
        {
            _value = v;
            int selected = -1;
            for (int i=0 ; i<_list.getItemCount() && selected < 0; i++)
            {
                String itemValue = _list.getValue(i);
                if (itemValue == null)
                {
                    if (_value != null)
                        continue;
                    selected = i;
                }
                else if (itemValue.equalsIgnoreCase(_value))
                    selected = i;
            }
            _list.setSelectedIndex(selected);
        }

        String getValue()
        {
            return _value;
        }

        public void onChange(Widget sender)
        {
            _value = _list.getValue(_list.getSelectedIndex());
            hide();
        }


        public void show()
        {
            _list.setVisibleItemCount(Math.max(2,Math.min(10,_list.getItemCount())));
            super.show();
        }
    }

    public void setTitle(String title)
    {
        _title = title;
    }
}
