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
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.gwt.client.util.IPropertyWrapper;
import org.labkey.api.gwt.client.util.StringProperty;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 24, 2007
 * Time: 2:10:20 PM
 */
public class PropertiesEditor implements LookupListener
{
    public static final String currentFolder = "[current folder]"; 

    public static final String statusAdded = "ADDED";
    public static final String statusDeleted = "DELETED";
    public static final String statusExisting = "EXISTING";
    public static final String statusChanged = "CHANGED";

    public static final String modeEdit = "edit";
    public static final String modeRead = "read";

    private DockPanel _panel;
    private PropertyPane _propertiesPane;
    private VerticalPanel _noColumnsPanel;
    private FlexTable _table;
    private ImageButton _addButtonNoProps;
    private HorizontalPanel _addButtonPanelProps;
    private String _mode;
    private LookupServiceAsync _lookupService;
    private List _listeners = new ArrayList();

    private GWTPropertyDescriptor _selectedPD;

    private class Row
    {
        Row()
        {
            edit = new GWTPropertyDescriptor();
            edit.setRangeURI("http://www.w3.org/2001/XMLSchema#string");
            edit.setRequired(false);
        }
        
        Row(GWTPropertyDescriptor p)
        {
            orig = p;
            edit = new GWTPropertyDescriptor(p);
        }

        GWTPropertyDescriptor orig;
        GWTPropertyDescriptor edit;
//        String status;
        boolean deleted;
        boolean readOnly;
        boolean locked;

        boolean _expanded = false;
    }

    GWTDomain _domain;
    ArrayList _rows;

    public PropertiesEditor(LookupServiceAsync service)
    {
        _rows = new ArrayList();

        _lookupService = service;

        _panel = new DockPanel();
        _panel.setWidth("100%");
        DOM.setElementAttribute(_panel.getElement(), "width", "100%");
        _table = new FlexTable();
        _table.setCellPadding(2);
        _table.setCellSpacing(0);
        _table.setWidth("100%");
        _table.setVisible(false);

        ClickListener addListener = new ClickListener()
        {
            public void onClick(Widget sender)
            {
                addPropertyDescriptor();
            }
        };
        _addButtonNoProps = new ImageButton("Add Field", addListener);
        ImageButton addButtonProps = new ImageButton("Add Field", addListener);
        _addButtonPanelProps = new HorizontalPanel();
        _addButtonPanelProps.add(addButtonProps);

        _noColumnsPanel = new VerticalPanel();
        _noColumnsPanel.add(new HTML("<br/>No fields have been defined.<br/>&nbsp;"));
        _noColumnsPanel.add(_addButtonNoProps);

        VerticalPanel propertiesListPanel = new VerticalPanel();
        propertiesListPanel.add(_noColumnsPanel);
        propertiesListPanel.add(_table);

        DockPanel propertyDock = new DockPanel();

        _propertiesPane = new PropertyPane(propertyDock.getElement(), this);
        _propertiesPane.addChangeListener(new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                if (_selectedPD != null)
                {
                    refreshRow(_selectedPD);
                }
                fireChangeEvent();
            }
        });
        _panel.add(propertiesListPanel, DockPanel.CENTER);
        _panel.setCellHorizontalAlignment(propertiesListPanel, HasHorizontalAlignment.ALIGN_LEFT);
        _panel.setCellVerticalAlignment(propertiesListPanel, HasVerticalAlignment.ALIGN_TOP);
        _panel.setCellWidth(propertiesListPanel, "100%");

        HorizontalPanel fillerPanel = new HorizontalPanel();
        propertyDock.add(_propertiesPane, DockPanel.NORTH);
        propertyDock.add(fillerPanel, DockPanel.CENTER);

        _panel.add(propertyDock, DockPanel.EAST);
        _panel.setCellHorizontalAlignment(propertyDock, HasHorizontalAlignment.ALIGN_RIGHT);
        _panel.setCellVerticalAlignment(propertyDock, HasVerticalAlignment.ALIGN_TOP);
        _panel.setCellHeight(propertyDock, "100%");
        fillerPanel.setHeight("100%");
        propertyDock.setHeight("100%");

        int col=0;
        col++;  // status
        col++;  // buttons
        col++;  // radio button
        _table.getFlexCellFormatter().setWidth(0, col, "120px");
        setBoldText(_table, 0, col++, "Name");
        _table.getFlexCellFormatter().setWidth(0, col, "120px");
        setBoldText(_table, 0, col++, "Label");
        _table.getFlexCellFormatter().setWidth(0, col, "155px");
        setBoldText(_table, 0, col++, "Type");

        _table.getFlexCellFormatter().setColSpan(0, col, 2);
        setBoldText(_table, 0, col, "Lookup");
        col += 2;

        _mode = modeRead;
    }

    public Panel getMainPanel()
    {
        return _panel;
    }

    public void init(GWTDomain domain)
    {
        _domain = domain;
        _rows = new ArrayList();

        List l = domain.getPropertyDescriptors();
        if (null != l)
        for (int i=0 ; i<l.size() ; i++)
        {
            GWTPropertyDescriptor p = (GWTPropertyDescriptor)l.get(i);
            _rows.add(new Row(p));
            _setReadOnly(i, !p.isEditable());
            if (domain.getRequiredPropertyDescriptors().contains(p.getName()))
                _setLocked(i, true);
        }

        refresh();
    }


    public GWTPropertyDescriptor addPropertyDescriptor()
    {
        _table.setVisible(true);
        _noColumnsPanel.setVisible(false);
        Row newRow = new Row();
        _rows.add(newRow);
        int index = _rows.size() - 1;
        _setReadOnly(index, false);
        refresh();

        select(newRow.edit);
        ((TextBox)_table.getWidget(index + 1, 3)).setFocus(true);
        return newRow.edit;
    }

    public void setPropertyDescriptors(List descriptors)
    {
        _selectedPD = null;
        _domain.setPropertyDescriptors(descriptors);
        init(_domain);
        fireChangeEvent();
    }


    void refresh()
    {
        while (_table.getRowCount() >= _rows.size() + 2)
            _table.removeRow(_table.getRowCount() - 1);

        _table.setVisible(!_rows.isEmpty());
        _noColumnsPanel.setVisible(_rows.isEmpty());

        for (int index=0 ; index < _rows.size() ; index++)
        {
            Row row = (Row)_rows.get(index);
            refreshRow(row.edit);
        }

        _table.setWidget(_table.getRowCount(), 3, _addButtonPanelProps);

        select(_selectedPD);
    }

    private void select(GWTPropertyDescriptor pd)
    {
        GWTPropertyDescriptor oldPD = _selectedPD;
        if (_selectedPD != null)
        {
            for (int row = 0; row < _table.getRowCount(); row++)
            {
                Element e = _table.getRowFormatter().getElement(row);
                DOM.setStyleAttribute(e, "backgroundColor", "#ffffff");
            }
        }

        _selectedPD = pd;

        if (_selectedPD != null)
        {
            int index = getRow(pd);

            String status = getStatus(pd);
            boolean readMode = getMode().equals(modeRead);  // control
            boolean readOnly = readMode || isReadOnly(index) || status.equals(statusDeleted);
            boolean locked = isLocked(index);

            int tableRow = index + 1;
            RadioButton radioButton = (RadioButton) _table.getWidget(tableRow, 2);

            _propertiesPane.showPropertyDescriptor(pd, !locked && !readOnly, radioButton.getAbsoluteTop());
            
            radioButton.setChecked(true);
            Element e = _table.getRowFormatter().getElement(tableRow);
            DOM.setStyleAttribute(e, "backgroundColor", "#eeeeee");
        }
        else
        {
            _propertiesPane.showPropertyDescriptor(pd, false);
        }

        if (oldPD != null)
        {
            refreshRow(oldPD);
        }
    }

    public void refreshRow(final GWTPropertyDescriptor pd)
    {
        final int index = getRow(pd);
        if (index == -1)
        {
            return;
        }
        
        final Row rowObject = (Row)_rows.get(index);
        final int tableRow = index + 1;
        int col = 0;

        String status = getStatus(pd);
        boolean readMode = getMode().equals(modeRead);  // control
        boolean readOnly = readMode || isReadOnly(index) || status.equals(statusDeleted);
        boolean locked = isLocked(index);

        if (!status.equalsIgnoreCase(statusExisting))
        {
            fireChangeEvent();
        }
        Image statusImage = getStatusImage(Integer.toString(index), status);
        statusImage.addMouseListener(new Tooltip(status));
        _table.setWidget(tableRow, col, statusImage);
        col++;

        if (!locked && !readMode && !isReadOnly(index))
        {
            if (status.equals(statusDeleted))
            {
                Image l = getCancelImage(index, new ClickListener() {
                    public void onClick(Widget sender)
                    {
                        markUndeleted(index);
                    }
                });
                l.addMouseListener(new Tooltip("Cancel Delete"));
                _table.setWidget(tableRow,col,l);
            }
            else
            {
                Image l = getDeleteImage(index, new ClickListener() {
                    public void onClick(Widget sender)
                    {
                        if (rowObject.orig == null)
                        {
                            _rows.remove(index);
                            if (_selectedPD == pd)
                            {
                                select(null);
                            }
                            refresh();
                        }
                        else
                        {
                            markDeleted(index);
                        }
                    }
                });
                l.addMouseListener(new Tooltip("Delete"));
                _table.setWidget(tableRow,col,l);
            }
        }
        else
            _table.setText(tableRow,col,"");
        col++;


        final RadioButton selectionRadioButton = new RadioButton("selectionRadioButton");
        selectionRadioButton.setChecked(_selectedPD == pd);
        selectionRadioButton.addClickListener(new ClickListener()
        {
            public void onClick(Widget sender)
            {
                select(pd);
            }
        });

        FocusListener focusListener = new FocusListener()
        {
            public void onFocus(Widget sender)
            {
                if (pd != _selectedPD)
                {
                    select(pd);
                }
            }

            public void onLostFocus(Widget sender)
            {
            }
        };

        _table.setWidget(tableRow, col, selectionRadioButton);
        col++;

        BoundTextBox nameTextBox = new BoundTextBox(pd, "name", "120", 200, "ff_name" + index)
        {
            String validateValue(String text)
            {
                if (text == null || isLegalName(text))
                    return null;
                return "Name may only contain letters, numbers, and underscore (_).";
            }
        };
        nameTextBox.addFocusListener(focusListener);
        nameTextBox.setEnabled(!locked && !readOnly);
        _table.setWidget(tableRow, col, nameTextBox);

        col++;

        BoundTextBox labelTextBox = new BoundTextBox(pd, "label", "120", 200, "ff_label" + index);
        labelTextBox.addFocusListener(focusListener);
        labelTextBox.setEnabled(!readOnly);
        _table.setWidget(tableRow, col, labelTextBox);
        col++;

        BoundTypePicker typePicker = new BoundTypePicker(index, "ff_type" + index, _domain.isAllowFileLinkProperties(), _domain.isAllowAttachmentProperties());
        typePicker.addFocusListener(focusListener);
        typePicker.setRangeURI(pd.getRangeURI());
        typePicker.setEnabled(status.equals(statusAdded));
        _table.setWidget(tableRow, col, typePicker);
        col++;

        if (!locked && !readOnly)
        {
            Image l = getPopupImage(Integer.toString(index), new ClickListener()
            {
                public void onClick(final Widget sender)
                {
                    select(pd);
                    editLookup(index);
               }
            });
            _table.setWidget(tableRow,col,l);
        }
        else
            _table.setText(tableRow,col,"");
        col++;

        String lookupString = "(none)";
        if (pd.getLookupQuery() != null && pd.getLookupQuery().length() > 0 &&
                pd.getLookupSchema() != null && pd.getLookupSchema().length() > 0)
        {
            lookupString = "" + pd.getLookupSchema() + "." + pd.getLookupQuery();
        }
        _table.setText(tableRow,col,lookupString);
        _table.getFlexCellFormatter().setWidth(tableRow,col,"900px");
    }

    public static Image getPopupImage(String index, ClickListener l)
    {
        String src = PropertyUtil.getContextPath() + "/_images/partdown.gif";
        Image i = new Image(src);
        DOM.setElementProperty(i.getElement(), "id", "partdown_" + index);
        if (null != l)
            i.addClickListener(l);
        return i;
    }

    Image getActionImage(String action, int index, ClickListener l)
    {
        String src = PropertyUtil.getContextPath() + "/_images/" + action + ".gif";
        Image i = new Image(src);
        DOM.setElementProperty(i.getElement(), "id", action + "_" + index);
        if (null != l)
            i.addClickListener(l);
        return i;
    }

    Image getDeleteImage(int index, ClickListener l)
    {
        return getActionImage("partdelete", index, l);
    }

    Image getCancelImage(int index, ClickListener l)
    {
        return getActionImage("cancel", index, l);
    }

    Image getStatusImage(String index, String status)
    {
        String src = PropertyUtil.getContextPath() + "/_images/part" + status.toLowerCase() + ".gif";
        Image i = new Image(src);
        DOM.setElementProperty(i.getElement(), "id", "part" + status.toLowerCase() + "_" + index);
        return i;
    }

    Row getRow(int i)
    {
        return (Row)(_rows.get(i));
    }


    public void _setReadOnly(int i, boolean b)
    {
        getRow(i).readOnly = b;
    }


    public void setReadOnly(int i, boolean b)
    {
        if (b != isReadOnly(i))
        {
            getRow(i).readOnly = b;
            if (!getMode().equals(modeRead))
                refresh();
        }
    }


    public boolean isLocked(int i)
    {
        return getRow(i).locked;
    }

    public void _setLocked(int i, boolean b)
    {
        getRow(i).locked = b;
    }


    public void setLocked(int i, boolean b)
    {
        if (b != isReadOnly(i))
        {
            getRow(i).locked = b;
            if (!getMode().equals(modeRead))
                refresh();
        }
    }


    public boolean isReadOnly(int i)
    {
        return getRow(i).readOnly;
    }

    public String getStatus(GWTPropertyDescriptor pd)
    {
        int i = getRow(pd);
        if (getRow(i).deleted)
            return statusDeleted;

        GWTPropertyDescriptor orig = getRow(i).orig;
        GWTPropertyDescriptor edit = getRow(i).edit;

        if (orig == null)
            return statusAdded;

        return edit.equals(orig) ? statusExisting : statusChanged;
    }


    public void markDeleted(int i)
    {
        getRow(i).deleted = true;
        refresh();
    }


    public void markUndeleted(int i)
    {
        getRow(i).deleted = false;
        refresh();
    }


    public List validate()
    {
        GWTDomain d = getDomainUpdates();
        Set names = new HashSet();
        List l = d.getPropertyDescriptors();
        Set errors = new HashSet();
        Set lowerCaseReservedNames = new HashSet();
        for (Iterator it = d.getReservedPropertyNames().iterator(); it.hasNext(); )
            lowerCaseReservedNames.add(((String) it.next()).toLowerCase());

        for (int i=0 ; i<l.size() ; i++)
        {
            GWTPropertyDescriptor p = (GWTPropertyDescriptor)l.get(i);
            String name = p.getName();
            if (null == name || name.length() == 0)
            {
                errors.add("Name field must not be blank");
                continue;
            }

            if (lowerCaseReservedNames.contains(name.toLowerCase()))
            {
                errors.add("\"" + name + "\" is a reserved field name in \"" + d.getName() + "\".");
                continue;
            }

            if (names.contains(name.toLowerCase()))
            {
                errors.add("All property names must be unique: " + name);
                continue;
            }

            if (!isLegalName(name))
            {
                errors.add("Name must only contain letters numbers and underscore (_)");
                continue;
            }
            
            names.add(name);
        }
        return errors.size() > 0 ? new ArrayList(errors) : null;
    }
    

    public GWTDomain getDomainUpdates()
    {
        _propertiesPane.copyValuesToPropertyDescriptor();
        GWTDomain d = _domain; // UNDONE COPY
        ArrayList l = new ArrayList();
        for (int i=0 ; i<_rows.size() ; i++)
        {
            Row r = getRow(i);
            String status = getStatus(r.edit);
            if (status.equals(statusDeleted))
                continue;
            r.edit.setName(StringUtils.trimToNull(r.edit.getName()));
            l.add(r.edit);
        }
        d.setPropertyDescriptors(l);
        return d;
    }

    public int getRow(GWTPropertyDescriptor pd)
    {
        for (int i = 0; i < getPropertyCount(); i++)
        {
            GWTPropertyDescriptor rowPD = getPropertyDescriptor(i);
            if (rowPD == pd)
            {
                return i;
            }
        }
        return -1;
    }
    
    public GWTPropertyDescriptor getPropertyDescriptor(int i)
    {
        return getRow(i).edit;
    }


    public int getPropertyCount()
    {
        return null == _rows ? 0 : _rows.size();
    }

    public int getPropertyCount(boolean includeDeleted)
    {
        if(includeDeleted || null == _rows)
            return getPropertyCount();
        else
        {
            int numProps = 0;
            for(int i = 0; i < _rows.size(); ++i)
            {
                if(!getRow(i).deleted)
                    numProps += 1;
            }
            return numProps;
        }
    } //getPropertyCount(boolean)


    public Widget getWidget()
    {
        return _panel;
    }


    public static void setBoldText(FlexTable t, int row, int col, String text)
    {
        // UNDONE: do this with styles
        t.setHTML(row, col, "<b>" + text + "</b>");
    }

    
    public String getMode()
    {
        return _mode;
    }


    public void setMode(String mode)
    {
        if (mode.equals(_mode))
            return;
        _mode = mode;
        _addButtonPanelProps.setVisible(!_mode.equals(modeRead));
        _addButtonNoProps.setVisible(!_mode.equals(modeRead));
        refresh();
    }

    public void addChangeListener(ChangeListener cl)
    {
        _listeners.add(cl);
    }

    public boolean isDirty()
    {
        if (null == _rows)
            return false;
        
        for (int i=0 ; i<_rows.size() ; i++)
        {
            if (!getRow(i).edit.equals(getRow(i).orig))
                return true;
        }
        return false;
    }

    /*
     * These widgets are bound in one direction, that is they push changes into the
     * underlying objects.
     *
     * Changes to the underlying objects (or edit state) are not reflected in the UI
     * without a call to refresh() or refreshRow().
     */

    private class BoundTypePicker extends TypePicker implements ChangeListener
    {
        int _index;
        GWTPropertyDescriptor _p;

        BoundTypePicker(int i, String id, boolean allowFileLinkProperties, boolean allowAttachmentProperties)
        {
            super(getRow(i).edit.getRangeURI(), allowFileLinkProperties, allowAttachmentProperties);
            _index = i;
            _p = getRow(_index).edit;
            DOM.setElementProperty(getElement(), "id", id);
            addChangeListener(this);
        }

        public void onChange(Widget sender)
        {
            String status = getStatus(_p);
            _p.setRangeURI(getRangeURI());
            _p.setFormat(null);
            refreshRow(_p);
        }
    }

    private void fireChangeEvent()
    {
        for (int i = 0; i < _listeners.size(); i++)
            ((ChangeListener) _listeners.get(i)).onChange(null);
    }

    private class BoundTextBox extends TextBox implements ChangeListener, FocusListener
    {
        GWTPropertyDescriptor _pd = null;
        IPropertyWrapper _prop;

        private BoundTextBox(String width, int maxLength, String id)
        {
            if (maxLength > 0)
                setMaxLength(maxLength);
            if (width != null && width.length()!=0)
                setWidth(width);
            DOM.setElementProperty(getElement(), "id", id);
            addChangeListener(this);
            addFocusListener(this);
            addKeyboardListener(new KeyboardListenerAdapter()
            {
                public void onKeyPress(Widget sender, char keyCode, int modifiers)
                {
                    fireChangeEvent();
                }
                public void onKeyUp(Widget sender, char keyCode, int modifiers)
                {
                    if (_prop instanceof StringProperty)
                        updateErrorFormat(false);
                }
            });
        }

        // this constructor knows how/when to refreshRow(i)
        BoundTextBox(GWTPropertyDescriptor pd, String name, String width, int maxLength, String id)
        {
            this(width, maxLength, id);
            _pd = pd;
            _prop = pd.bindProperty(name);
            refresh();
        }

        BoundTextBox(IPropertyWrapper prop, String width, int maxLength, String id)
        {
            this(width, maxLength, id);
            _prop = prop;
            refresh();
        }

        /** retrieve/reshow data from bound object */
        void refresh()
        {
            setText(StringUtils.trimToEmpty((String)_prop.get()));
            updateErrorFormat(false);
        }

        void updateErrorFormat(boolean alert)
        {
            String text = StringUtils.trimToNull(getText());
            String message = validateValue(text);
            if (null != message)
            {
                this.setStyleName("labkey-textbox-error");
                this.setTitle(message);
                if (alert)
                    Window.alert(message);
                return;
            }
            this.setStyleName("");
            this.setTitle("");
        }

        String validateValue(String text)
        {
            return null;
        }

        /** push data to bound object */
        void update()
        {
            updateErrorFormat(false);
            String text = StringUtils.trimToNull(getText());

            if (_pd != null)
            {
                String status = getStatus(_pd);
                Object propObj = _prop.get();
                String propText = propObj == null ? null : propObj.toString();
                if (!nullEquals(text, propText))
                {
                    _prop.set(text);
                    if (!status.equals(getStatus(_pd)))
                        refreshRow(_pd);
                    fireChangeEvent();
                }
            }
            else
            {
                _prop.set(text);
                fireChangeEvent();
            }
        }


        public void onLostFocus(Widget sender)
        {
            update();
        }

        public void onFocus(Widget sender)
        {
        }

        public void onChange(Widget sender)
        {
            update();
        }

        private boolean nullEquals(Object s1, Object s2)
        {
            if (s1 == null)
            {
                return s2 == null;
            }
            else
            {
                return s1.equals(s2);
            }
        }
    }


    public void editLookup(int row)
    {
        if (null == _lookupService)
            return;

        final LookupEditor lookupEditor = new LookupEditor(_lookupService, this);

        lookupEditor.init(getPropertyDescriptor(row));
        PopupPanel.PositionCallback callback = new PopupPanel.PositionCallback()
        {
            public void setPosition(int offsetWidth, int offsetHeight)
            {
                WindowUtil.centerDialog(lookupEditor);
            }
        };

        lookupEditor.setPopupPositionAndShow(callback);

        fireChangeEvent();
    }


    public void lookupUpdated(GWTPropertyDescriptor pd)
    {
        int row = getRow(pd);
        if (row != -1)
        {
            refreshRow(pd);
        }
    }


    private static boolean isLegalNameChar(char ch, boolean first)
    {
        if (ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z' || ch == '_')
            return true;
        if (first)
            return false;
        if (ch >= '0' && ch <= '9')
            return true;
        return false;
    }

    public static boolean isLegalName(String str)
    {
/* BUG 5518
        for (int i = 0; i < str.length(); i ++)
        {
            if (!isLegalNameChar(str.charAt(i), i == 0))
                return false;
        }
*/
        return true;
    }

}