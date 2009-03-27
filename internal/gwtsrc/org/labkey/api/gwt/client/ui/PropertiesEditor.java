/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
import org.labkey.api.gwt.client.ui.property.*;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 24, 2007
 * Time: 2:10:20 PM
 */
public class PropertiesEditor<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> implements LookupListener<FieldType>
{
    public static final String currentFolder = "[current folder]";

    public enum FieldStatus
    {
        Added("This field is newly added"),
        Deleted("This field is marked for deletion"),
        Existing("This field has not been changed"),
        Changed("This field has been edited");

        private final String _description;

        private FieldStatus(String description)
        {
            _description = description;
        }

        public String getDescription()
        {
            return _description;
        }
    }

    private Saveable<GWTDomain> _owner;
    private DockPanel _panel;
    private PropertyPane<DomainType, FieldType> _propertiesPane;
    private VerticalPanel _noColumnsPanel;
    private FlexTable _table;
    private HorizontalPanel _buttonPanel;
    private boolean _readOnly;
    protected LookupServiceAsync _lookupService;
    private List<ChangeListener> _listeners = new ArrayList<ChangeListener>();

    private FieldType _selectedPD;
    private ImageButton _addFieldButton;
    private ImageButton _importSchemaButton;
    private ImageButton _exportSchemaButton;
    private ImageButton _inferSchemaButton;
    private DefaultValueItem<DomainType, FieldType> _defaultValueSelector;

    protected DomainType _domain;
    ArrayList<Row> _rows;

    private class Row
    {
        Row(FieldType p)
        {
            orig = p.getPropertyId() == 0 ? null : p;
            edit = (FieldType)p.copy();
        }

        FieldType orig;
        FieldType edit;
        boolean deleted;
    }

    public PropertiesEditor(Saveable<GWTDomain> owner, LookupServiceAsync service)
    {
        _rows = new ArrayList<Row>();

        _lookupService = service;
        _owner = owner;

        _panel = new DockPanel();
        _panel.setWidth("100%");
        DOM.setElementAttribute(_panel.getElement(), "width", "100%");
        _table = new FlexTable();
        _table.setCellPadding(2);
        _table.setCellSpacing(0);
        _table.setWidth("100%");
        _table.setVisible(false);

        _noColumnsPanel = new VerticalPanel();
        _noColumnsPanel.add(new HTML("<br/>No fields have been defined.<br/>&nbsp;"));

        ClickListener addListener = new ClickListener()
        {
            public void onClick(Widget sender)
            {
                GWTPropertyDescriptor prop = new GWTPropertyDescriptor();
                prop.setDefaultValueType(_domain.getDefaultDefaultValueType());
                addField((FieldType) prop);
            }
        };

        _buttonPanel = new HorizontalPanel();

        _addFieldButton = new ImageButton("Add Field", addListener);

        ClickListener importSchemaListener = new ClickListener()
        {
            public void onClick(Widget sender)
            {
                final ImportSchemaWizard popup = new ImportSchemaWizard(PropertiesEditor.this);
                popup.setText("Import Schema");
                popup.center();
            }
        };

        ClickListener exportSchemaListener = new ClickListener()
        {
            public void onClick(Widget sender)
            {
                final ExportSchemaWizard popup = new ExportSchemaWizard(PropertiesEditor.this);
                popup.setText("Export Schema");
                popup.center();
            }
        };

        ClickListener inferSchemaListener = new ClickListener()
        {
            public void onClick(Widget sender)
            {
                final InferSchemaWizard popup = new InferSchemaWizard(PropertiesEditor.this);
                popup.setText("Infer Schema");
                popup.center();
            }
        };

        _importSchemaButton = new ImageButton("Import Schema", importSchemaListener);
        _exportSchemaButton = new ImageButton("Export Schema", exportSchemaListener);
        _inferSchemaButton = new ImageButton("Infer Schema from File", inferSchemaListener);

        refreshButtons(_buttonPanel);

        VerticalPanel propertiesListPanel = new VerticalPanel();
        propertiesListPanel.add(_noColumnsPanel);
        propertiesListPanel.add(_table);
        propertiesListPanel.add(_buttonPanel);

        DockPanel propertyDock = new DockPanel();

        _propertiesPane = createPropertyPane(propertyDock);
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

        _readOnly = false;
    }

    protected PropertyPane<DomainType, FieldType> createPropertyPane(DockPanel propertyDock)
    {
        PropertyPane<DomainType, FieldType> propertyPane = new PropertyPane<DomainType, FieldType>(propertyDock.getElement(), this);
        propertyPane.addItem(new FormatItem<DomainType, FieldType>(propertyPane));
        RequiredItem<DomainType, FieldType> requiredItem = new RequiredItem<DomainType, FieldType>(propertyPane);
        propertyPane.addItem(requiredItem);
        // 7441 : de-clutter PropertyDescriptor editor, remove Hidden checkbox
        //propertyPane.addItem(new HiddenItem<DomainType, FieldType>(propertyPane));
        propertyPane.addItem(new QcEnabledItem<DomainType, FieldType>(propertyPane));
        _defaultValueSelector = new DefaultValueItem<DomainType, FieldType>(_owner, propertyPane);
        propertyPane.addItem(_defaultValueSelector);
        propertyPane.addItem(new DescriptionItem<DomainType, FieldType>(propertyPane));
        propertyPane.addItem(new ValidatorItem<DomainType, FieldType>(propertyPane));
        return propertyPane;
    }

    public Saveable getOwner()
    {
        return _owner;
    }

    public DomainType getCurrentDomain()
    {
        return _domain;
    }

    public Panel getMainPanel()
    {
        return _panel;
    }

    public void init(DomainType domain)
    {
        _domain = domain;
        _rows = new ArrayList<Row>();

        List<FieldType> fields = domain.getFields();
        if (null != fields)
        {
            for (FieldType field : fields)
            {
                _rows.add(new Row(field));
            }
        }
        fireChangeEvent();

        refresh();
    }


    public FieldType addField(FieldType field)
    {
        _table.setVisible(true);
        _noColumnsPanel.setVisible(false);
        Row newRow = new Row(field);
        _rows.add(newRow);
        int index = _rows.size() - 1;
        refresh();

        select(newRow.edit);
        ((TextBox)_table.getWidget(index + 1, 3)).setFocus(true);
        return newRow.edit;
    }

    public void setPropertyDescriptors(List<FieldType> descriptors)
    {
        _selectedPD = null;
        _domain.setFields(descriptors);
        init(_domain);
        fireChangeEvent();
    }

    void refresh()
    {
        while (_table.getRowCount() >= _rows.size() + 2)
            _table.removeRow(_table.getRowCount() - 1);

        _table.setVisible(!_rows.isEmpty());
        _noColumnsPanel.setVisible(_rows.isEmpty());

        for (Row row : _rows)
        {
            refreshRow(row.edit);
        }

        select(_selectedPD);
    }

    protected void refreshButtons(HorizontalPanel buttonPanel)
    {
        if (_readOnly)
        {
            if (buttonPanel.getWidgetCount() == 1)
                return;
            buttonPanel.clear();
            buttonPanel.add(_exportSchemaButton);
        }
        else
        {
            if (buttonPanel.getWidgetCount() > 1)
                return;
            buttonPanel.clear();
            buttonPanel.add(_addFieldButton);
            buttonPanel.add(_importSchemaButton);
            buttonPanel.add(_exportSchemaButton);
            buttonPanel.add(_inferSchemaButton);
        }
    }

    private void select(FieldType pd)
    {
        FieldType oldPD = _selectedPD;
        if (_selectedPD != null)
        {
            for (int row = 0; row < _table.getRowCount(); row++)
            {
                Element e = _table.getRowFormatter().getElement(row);
                DOM.setStyleAttribute(e, "backgroundColor", "#ffffff");
            }
        }

        _selectedPD = pd;
        int index = getRow(pd);

        if (index != -1)
        {
            FieldStatus status = getStatus(pd);
            boolean readOnly = _readOnly || status == FieldStatus.Deleted;
            boolean locked = isLocked(index);

            int tableRow = index + 1;
            RadioButton radioButton = (RadioButton) _table.getWidget(tableRow, 2);

            _propertiesPane.showPropertyDescriptor(pd, !readOnly, radioButton.getAbsoluteTop());
            _defaultValueSelector.setEnabled(_domain.getDefaultValueOptions().length > 0);

            radioButton.setChecked(true);
            Element e = _table.getRowFormatter().getElement(tableRow);
            DOM.setStyleAttribute(e, "backgroundColor", "#eeeeee");
        }
        else
        {
            _propertiesPane.showPropertyDescriptor(null, false);
        }

        if (oldPD != null)
        {
            refreshRow(oldPD);
        }
    }

    public void refreshRow(final FieldType pd)
    {
        final int index = getRow(pd);
        if (index == -1)
        {
            return;
        }

        final Row rowObject = _rows.get(index);
        final int tableRow = index + 1;
        int col = 0;

        FieldStatus status = getStatus(pd);
        boolean readOnly = _readOnly || status == FieldStatus.Deleted;
        boolean locked = isLocked(index);

        Image statusImage = getStatusImage(Integer.toString(index), status);
        if (status != FieldStatus.Existing)
        {
            fireChangeEvent();
            statusImage.addMouseListener(new Tooltip(status.getDescription()));
        }
        _table.setWidget(tableRow, col, statusImage);
        col++;

        if (!locked && !_readOnly && !_domain.isMandatoryField(pd))
        {
            if (status == FieldStatus.Deleted)
            {
                Image l = getCancelImage(index, new ClickListener() {
                    public void onClick(Widget sender)
                    {
                        markUndeleted(index);
                    }
                });
                l.addMouseListener(new Tooltip("Click to cancel deletion"));
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
                l.addMouseListener(new Tooltip("Click to delete"));
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
        nameTextBox.setEnabled(!locked && !readOnly && !_domain.isMandatoryField(pd));
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
        typePicker.setEnabled(isTypeEditable(pd, status) && !locked);
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
            l.addMouseListener(new Tooltip("Click to edit the lookup"));
            _table.setWidget(tableRow,col,l);
        }
        else
            _table.setText(tableRow,col,"");
        col++;

        _table.setText(tableRow,col,pd.getLookupDescription());
        _table.getFlexCellFormatter().setWidth(tableRow,col,"900px");
    }

    protected boolean isTypeEditable(GWTPropertyDescriptor pd, FieldStatus status)
    {
        return status == FieldStatus.Added;
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

    Image getStatusImage(String index, FieldStatus status)
    {
        String src = PropertyUtil.getContextPath() + "/_images/part" + status.toString().toLowerCase() + ".gif";
        Image i = new Image(src);
        DOM.setElementProperty(i.getElement(), "id", "part" + status.toString().toLowerCase() + "_" + index);
        return i;
    }

    Row getRow(int i)
    {
        return _rows.get(i);
    }


    public boolean isLocked(int i)
    {
        return !_domain.isEditable(getRow(i).edit);
    }

    public FieldStatus getStatus(GWTPropertyDescriptor pd)
    {
        int i = getRow(pd);
        if (getRow(i).deleted)
            return FieldStatus.Deleted;

        GWTPropertyDescriptor orig = getRow(i).orig;
        GWTPropertyDescriptor edit = getRow(i).edit;

        if (orig == null)
            return FieldStatus.Added;

        return edit.equals(orig) ? FieldStatus.Existing : FieldStatus.Changed;
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


    public List<String> validate()
    {
        DomainType d = getUpdates();
        Set<String> names = new HashSet<String>();
        List<FieldType> l = d.getFields();
        Set<String> errors = new HashSet<String>();
        Set<String> lowerCaseReservedNames = new HashSet<String>();
        for (String name : d.getReservedFieldNames())
            lowerCaseReservedNames.add(name.toLowerCase());

        for (FieldType p : l)
        {
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

            names.add(name.toLowerCase());
        }
        return new ArrayList<String>(errors);
    }


    public DomainType getUpdates()
    {
        _propertiesPane.copyValuesToPropertyDescriptor();
        DomainType d = _domain; // UNDONE COPY
        ArrayList<FieldType> l = new ArrayList<FieldType>();
        for (int i=0 ; i<_rows.size() ; i++)
        {
            Row r = getRow(i);
            FieldStatus status = getStatus(r.edit);
            if (status == FieldStatus.Deleted)
                continue;
            r.edit.setName(StringUtils.trimToNull(r.edit.getName()));
            l.add(r.edit);
        }
        d.setFields(l);
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

    public FieldType getPropertyDescriptor(int i)
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

    public boolean isReadOnly()
    {
        return _readOnly;
    }

    public void setReadOnly(boolean readOnly)
    {
        _readOnly = readOnly;
        refreshButtons(_buttonPanel);
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
        FieldType _p;

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
            _p.setRangeURI(getRangeURI());
            _p.setFormat(null);
            refreshRow(_p);
        }
    }

    private void fireChangeEvent()
    {
        for (ChangeListener listener : _listeners)
            listener.onChange(null);
    }

    private class BoundTextBox extends TextBox implements ChangeListener, FocusListener
    {
        FieldType _pd = null;
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
        BoundTextBox(FieldType pd, String name, String width, int maxLength, String id)
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
                FieldStatus status = getStatus(_pd);
                Object propObj = _prop.get();
                String propText = propObj == null ? null : propObj.toString();
                if (!nullEquals(text, propText))
                {
                    _prop.set(text);
                    if (status != getStatus(_pd))
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

        final LookupEditor<FieldType> lookupEditor = createLookupEditor();

        lookupEditor.init(getPropertyDescriptor(row));

        lookupEditor.setPopupPositionAndShow(WindowUtil.createPositionCallback(lookupEditor));

        fireChangeEvent();
    }

    protected LookupEditor<FieldType> createLookupEditor()
    {
        return new LookupEditor<FieldType>(_lookupService, this, true);
    }


    public void lookupUpdated(FieldType pd)
    {
        int row = getRow(pd);
        if (row != -1)
        {
            refreshRow(pd);
        }
    }


    public static boolean isLegalNameChar(char ch, boolean first)
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
        for (int i = 0; i < str.length(); i ++)
        {
            if (!isLegalNameChar(str.charAt(i), i == 0))
                return false;
        }
        return true;
    }

    public void addButton(ImageButton imageButton)
    {
        _buttonPanel.add(imageButton);
    }

    /**
     * Transform an illegal name into a safe version. All non-letter characters
     * become underscores, and the first character must be a letter
     */
    public static String sanitizeName(String originalName)
    {
        StringBuilder sb = new StringBuilder();
        boolean first = true; // first character is special
        for (int i=0; i<originalName.length(); i++)
        {
            char c = originalName.charAt(i);
            if (isLegalNameChar(c, first))
            {
                sb.append(c);
                first = false;
            }
            else if (!first)
            {
                sb.append('_');
            }
        }
        return sb.toString();
    }
}