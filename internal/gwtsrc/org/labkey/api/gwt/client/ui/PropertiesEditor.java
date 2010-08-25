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

import com.extjs.gxt.ui.client.event.*;
import com.extjs.gxt.ui.client.widget.TabItem;
import com.extjs.gxt.ui.client.widget.form.Field;
import com.extjs.gxt.ui.client.widget.form.TextField;
import com.extjs.gxt.ui.client.widget.form.Validator;
import com.extjs.gxt.ui.client.widget.layout.FitLayout;
import com.google.gwt.event.dom.client.*;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.property.*;
import org.labkey.api.gwt.client.util.IPropertyWrapper;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.StringUtils;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 24, 2007
 * Time: 2:10:20 PM
 */
public class PropertiesEditor<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor>
{
    boolean useConceptPicker = true;
    
    public static class PD extends PropertiesEditor<GWTDomain<GWTPropertyDescriptor>,GWTPropertyDescriptor>
    {
        public PD(Saveable<GWTDomain> owner, LookupServiceAsync service)
        {
            super(owner, service, new GWTPropertyDescriptor());
        }
    }

    public static final String currentFolder = "[current folder]";
    protected VerticalPanel _contentPanel;
    private com.extjs.gxt.ui.client.widget.TabPanel _extraPropertiesTabPanel = new com.extjs.gxt.ui.client.widget.TabPanel();
    private Image _spacerImage;
    private boolean _warnedAboutDelete = false;
    private static final String BAD_NAME_ERROR_MESSAGE = "Name may only contain letters, numbers, spaces, and underscores (_), and must start with a letter or underscore.";

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
    private List<PropertyPane<DomainType, FieldType>> _propertiesPanes;
    private VerticalPanel _noColumnsPanel;
    private FlexTable _table;
    private HorizontalPanel _buttonPanel;
    private boolean _readOnly = false;
    protected CachingLookupService _lookupService;
    private List<ChangeHandler> _listeners = new ArrayList<ChangeHandler>();

    private FieldType _selectedPD;
    private ImageButton _addFieldButton;
    private ImageButton _importSchemaButton;
    private ImageButton _exportSchemaButton;
    private ImageButton _inferSchemaButton;
    private DefaultValueItem<DomainType, FieldType> _defaultValueSelector;

    protected DomainType _domain;
    ArrayList<Row> _rows;
    FieldType _newPropertyDescriptor;

    String prefixInputId = "";

    protected class Row
    {
        Row(FieldType p)
        {
            orig = p.getPropertyId() == 0 ? null : p;
            edit = (FieldType)p.copy();
        }

        public FieldType orig;
        public FieldType edit;
        public boolean deleted;
    }

    @Deprecated
    public PropertiesEditor(Saveable<GWTDomain> owner, LookupServiceAsync service)
    {
        this(owner, service, null);
    }
    
    public PropertiesEditor(Saveable<GWTDomain> owner, LookupServiceAsync service, FieldType empty)
    {
        _newPropertyDescriptor = empty;
        _rows = new ArrayList<Row>();

        _lookupService = new CachingLookupService(service);
        _owner = owner;

        _panel = new DockPanel();
        _panel.setWidth("900");

        _table = new FlexTable();
        _table.addStyleName("labkey-pad-cells"); // padding:5
        _table.setCellPadding(2);   // doesn't work inside extContainer!
        _table.setCellSpacing(0);   // causes spaces in highlight background if != 0
        _table.setWidth("100%");
        _table.setVisible(false);

        _noColumnsPanel = new VerticalPanel();
        _noColumnsPanel.add(new HTML("<br/>No fields have been defined.<br/>&nbsp;"));

        ClickHandler rowBubbleHandler = new ClickHandler()
        {
            public void onClick(ClickEvent event)
            {
                HTMLTable.Cell cell = _table.getCellForEvent(event);
                int row = cell.getRowIndex();
                int i = row-1;
                if (i >= 0 && i < _rows.size())
                    select(i);
            }
        };

        _table.addClickHandler(rowBubbleHandler);

        ClickHandler addListener = new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                GWTPropertyDescriptor prop = new GWTPropertyDescriptor();
                prop.setDefaultValueType(_domain.getDefaultDefaultValueType());
                addField((FieldType) prop);
            }
        };

        _buttonPanel = new HorizontalPanel();

        _addFieldButton = new ImageButton("Add Field", addListener);

        ClickHandler importSchemaListener = new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                final ImportSchemaWizard popup = new ImportSchemaWizard(PropertiesEditor.this);
                popup.setText("Import Fields");
                popup.center();
            }
        };

        ClickHandler exportSchemaListener = new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                final ExportSchemaWizard popup = new ExportSchemaWizard(PropertiesEditor.this);
                popup.setText("Export Fields");
                popup.center();
            }
        };

        ClickHandler inferSchemaListener = new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                ImageButton confirmButton = new ImageButton("OK", new ClickHandler()
                {
                    public void onClick(ClickEvent event)
                    {
                        final InferSchemaWizard popup = new InferSchemaWizard(PropertiesEditor.this);
                        popup.setText("Infer Fields");
                        popup.center();
                    }
                });
                WindowUtil.showConfirmDialog("Infer Fields From File", "This will delete all existing fields and data. Are you sure you wish to proceed?", confirmButton);
            }
        };

        _importSchemaButton = new ImageButton("Import Fields", importSchemaListener);
        _exportSchemaButton = new ImageButton("Export Fields", exportSchemaListener);
        _inferSchemaButton = new ImageButton("Infer Fields from File", inferSchemaListener);

        _contentPanel = new VerticalPanel();
        _contentPanel.add(_noColumnsPanel);
        _contentPanel.add(_table);
        _contentPanel.add(_buttonPanel);

        DockPanel propertyDock = new DockPanel();

        _propertiesPanes = createPropertyPanes(propertyDock);

        ChangeListener listener = new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                if (_selectedPD != null)
                {
//                    refreshRow(_selectedPD);
                }
                fireChangeEvent();
            }
        };
        for (PropertyPane<DomainType, FieldType> propertiesPane : _propertiesPanes)
        {
            propertiesPane.addChangeListener(listener);
            DockPanel wrapper = new DockPanel();
            // Stick the properties panel in a DockPanel so that it doesn't get stretched to fill the full
            // TabPanel size
            wrapper.add(propertiesPane, DockPanel.NORTH);

            TabItem ti = new TabItem(propertiesPane.getName());
            ti.setLayout(new FitLayout());
            ti.add(wrapper);
            _extraPropertiesTabPanel.add(ti);
        }

        _extraPropertiesTabPanel.setPlain(true);
        _extraPropertiesTabPanel.addListener(Events.Render, new Listener<ComponentEvent>(){
            public void handleEvent(ComponentEvent be)
            {
                _extraPropertiesTabPanel.getLayoutTarget().setStyleAttribute("backgroundColor","#eeeeee");
            }
        });
        _extraPropertiesTabPanel.setPixelSize(350, getExtraPropertiesHeight());
        _extraPropertiesTabPanel.setSelection(_extraPropertiesTabPanel.getItem(0));
        _extraPropertiesTabPanel.addListener(Events.Select, new Listener<TabPanelEvent>()
        {
            public void handleEvent(TabPanelEvent integerSelectionEvent)
            {
                repositionExtraProperties();
            }
        });

        _spacerImage = new Image(PropertyUtil.getContextPath() + "/_.gif");
        _spacerImage.setPixelSize(1, 1);

        _panel.add(_contentPanel, DockPanel.CENTER);
        _panel.setCellHorizontalAlignment(_contentPanel, HasHorizontalAlignment.ALIGN_LEFT);
        _panel.setCellVerticalAlignment(_contentPanel, HasVerticalAlignment.ALIGN_TOP);
        _panel.setCellWidth(_contentPanel, "100%");

        propertyDock.add(_spacerImage, DockPanel.NORTH);
        propertyDock.add(_extraPropertiesTabPanel, DockPanel.CENTER);
        propertyDock.setVerticalAlignment(HasVerticalAlignment.ALIGN_TOP);

        _panel.add(propertyDock, DockPanel.EAST);
        _panel.setCellHorizontalAlignment(propertyDock, HasHorizontalAlignment.ALIGN_RIGHT);
        _panel.setCellVerticalAlignment(propertyDock, HasVerticalAlignment.ALIGN_TOP);

        int col=0;
        col++;  // status
        col++;  // delete button
        col++;  // up button
        col++;  // down button
        col++;  // decoration (e.g. PK)
        assert col == COLUMN_OF_NAMEFIELD;
        _table.getFlexCellFormatter().setWidth(0, col, "120px");
        setBoldText(_table, 0, col++, "Name");
        _table.getFlexCellFormatter().setWidth(0, col, "120px");
        setBoldText(_table, 0, col++, "Label");
        _table.getFlexCellFormatter().setWidth(0, col, "155px");
        setBoldText(_table, 0, col++, "Type");

        if (!useConceptPicker)
        {
            _table.getFlexCellFormatter().setColSpan(0, col, 2);
            setBoldText(_table, 0, col, "Lookup");
            col += 2;
        }

        select(null, true);
        _readOnly = false;
        refreshButtons(_buttonPanel);
    }


    protected int getExtraPropertiesHeight()
    {
        return 250;
    }

    protected List<PropertyPane<DomainType, FieldType>> createPropertyPanes(DockPanel propertyDock)
    {
        PropertyPane<DomainType, FieldType> displayPane = new PropertyPane<DomainType, FieldType>(this, "Display");
        displayPane.addItem(new DescriptionItem<DomainType, FieldType>(displayPane));
        displayPane.addItem(new URLItem<DomainType, FieldType>(displayPane));
        displayPane.addItem(new VisibilityItem<DomainType, FieldType>(displayPane));

        PropertyPane<DomainType, FieldType> formatPane = new PropertyPane<DomainType, FieldType>(this, "Format");
        formatPane.addItem(new FormatItem<DomainType, FieldType>(formatPane));
        formatPane.addItem(new ConditionalFormatItem<DomainType, FieldType>(formatPane));

        PropertyPane<DomainType, FieldType> validatorPane = new PropertyPane<DomainType, FieldType>(this, "Validators");
        validatorPane.addItem(new RequiredItem<DomainType, FieldType>(validatorPane));
        validatorPane.addItem(new ValidatorItem<DomainType, FieldType>(validatorPane));

        PropertyPane<DomainType, FieldType> advancedPane = new PropertyPane<DomainType, FieldType>(this, "Advanced");
        advancedPane.addItem(new MvEnabledItem<DomainType, FieldType>(advancedPane));
        _defaultValueSelector = new DefaultValueItem<DomainType, FieldType>(_owner, advancedPane);
        advancedPane.addItem(_defaultValueSelector);
        advancedPane.addItem(new ImportAliasesItem<DomainType, FieldType>(advancedPane));
        advancedPane.addItem(new MeasureItem<DomainType, FieldType>(advancedPane));
        advancedPane.addItem(new DimensionItem<DomainType, FieldType>(advancedPane));

        List<PropertyPane<DomainType, FieldType>> result = new ArrayList<PropertyPane<DomainType, FieldType>>();
        result.add(displayPane);
        result.add(formatPane);
        result.add(validatorPane);
        result.add(advancedPane);
        return result;
    }

    public Saveable getOwner()
    {
        return _owner;
    }

    public DomainType getCurrentDomain()
    {
        return _domain;
    }

    public VerticalPanel getContentPanel()
    {
        return _contentPanel;
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
                // we assume that propertyId==0 usually means this is a new field
                // however, assay round trips uncreated mandatory fields through the editor, so mark them as not-new
                if (domain.isMandatoryField(field) && field.getPropertyId() == 0)
                    field.setPropertyId(-1);
                _rows.add(new Row(field));
            }
        }
        fireChangeEvent();

        refresh();
        select(0);
    }


    int COLUMN_OF_NAMEFIELD = 5;
    
    public FieldType addField(FieldType field)
    {
        _log("-- ADD FIELD --");
        _table.setVisible(true);
        _noColumnsPanel.setVisible(false);
        Row newRow = new Row(field);
        int index = _rows.size();
        _rows.add(newRow);
        refreshRow(index, newRow);
        select(newRow.edit);
        setFocus(index);
        return newRow.edit;
    }


    public void setFocus(int row)
    {
        try
        {
            Widget w = _table.getWidget(row + 1, COLUMN_OF_NAMEFIELD);
            if (w instanceof Field)
                ((TextField)w).focus();
            if (w instanceof FocusWidget)
                ((FocusWidget)w).setFocus(true);
        }
        catch (Exception x)
        {
        }
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

        select(_selectedPD, true);
    }


//    void saveRow(int index)
//    {
//        int tableRow = index+1;
//        if (tableRow >= _table.getRowCount())
//            return;
//        for (int col=0 ; col<_table.getCellCount(tableRow) ; col++)
//        {
//            Widget w = _table.getWidget(tableRow,col);
//            if (w instanceof BoundWidget)
//            {
//                ((BoundWidget)w).validate();
//                ((BoundWidget)w).pushValue();
//            }
//        }
//    }
    

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


    private void select(int index)
    {
        if (index >= _rows.size())
            select(null);
        else
            select(_rows.get(index).edit);
    }
    

    private void select(FieldType pd)
    {
        select(pd, false);
    }


    // force when readonly changes for instance
    private void select(FieldType pd, boolean force)
    {
        if (pd == _selectedPD && !force)
            return;

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
            boolean readOnly = isReadOnly(getRow(index));

            int tableRow = index + 1;

            for (PropertyPane<DomainType, FieldType> propertiesPane : _propertiesPanes)
            {
                propertiesPane.showPropertyDescriptor(pd, !readOnly);
            }
            if (_defaultValueSelector != null)
            {
                _defaultValueSelector.setEnabled(_domain.getDefaultValueOptions().length > 0);
            }

            Element e = _table.getRowFormatter().getElement(tableRow);
            DOM.setStyleAttribute(e, "backgroundColor", "#eeeeee");
            _extraPropertiesTabPanel.setVisible(true);

            repositionExtraProperties();
        }
        else
        {
            _extraPropertiesTabPanel.setVisible(false);
            for (PropertyPane<DomainType, FieldType> propertiesPane : _propertiesPanes)
            {
                propertiesPane.showPropertyDescriptor(null, false);
            }
        }

        if (oldPD != null)
        {
            refreshRow(oldPD);
        }
    }


    protected boolean isReorderable()
    {
        return !_readOnly;
    }
    
    private void repositionExtraProperties()
    {
        if (_extraPropertiesTabPanel.isVisible() && _selectedPD != null)
        {
            int tableRow = getRow(_selectedPD) + 1;
            Element rowElement = _table.getRowFormatter().getElement(tableRow);
            int desiredBottom = rowElement.getAbsoluteTop() + rowElement.getOffsetHeight();

            int newSpacerHeight = desiredBottom - _table.getAbsoluteTop() - _extraPropertiesTabPanel.getOffsetHeight();
            if (newSpacerHeight < 0)
            {
                newSpacerHeight = 0;
            }

            _spacerImage.setHeight(newSpacerHeight + "px");
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
        refreshRow(index, rowObject);
    }


    class RowWidgetListener implements FocusHandler, Listener<ComponentEvent>, KeyPressHandler
    {
        FieldType pd;
        int index;

        RowWidgetListener(FieldType pd, int index)
        {
            this.pd = pd;
            this.index = index;
        }

        public void onFocus(FocusEvent event)
        {
            focus();
        }

        public void handleEvent(ComponentEvent e)
        {
            if (e.getType() ==  Events.KeyPress)
                componentKeyPress(e);
            else if (e.getType() == Events.Focus)
                focus();
        }

        public void onKeyPress(KeyPressEvent event)
        {
            if (event.getCharCode() == 13)
                enter();
        }

        public void componentKeyPress(ComponentEvent event)
        {
            if (event.getKeyCode() == 13)
                enter();
        }

        private void enter()
        {
            if (index < _rows.size()-1)
                setFocus(index+1);
            else if (null != _newPropertyDescriptor)
                addField(_newPropertyDescriptor);
        }

        private void focus()
        {
            PropertiesEditor.this.select(pd);
        }
    }


    class ColumnNameValidator implements WarningValidator
    {
        public String validate(Field<?> field, String value)
        {
            if (value == null || PropertiesEditorUtil.isLegalName(value))
                return null;
            return BAD_NAME_ERROR_MESSAGE;

        }
        public String warning(Field<?> field, String text)
        {
            if (text != null && text.contains(" "))
            {
                return "Name should not contain spaces.";
            }
            return null;
        }
    }


    public void refreshRow(final int index, final Row rowObject)
    {
        HTMLTable.CellFormatter formatter = _table.getCellFormatter();

        final FieldType pd = rowObject.edit;
        int tableRow = index+1;
        int col = 0;

        FieldStatus status = getStatus(rowObject);
        boolean readOnly = isReadOnly(rowObject);

        String imageId = "part" + status.toString().toLowerCase() + "_" + index;
        Image statusImage = getStatusImage(imageId, status, rowObject);
        if (status != FieldStatus.Existing)
            fireChangeEvent();
        _table.setWidget(tableRow, col, statusImage);
        col++;

        if (isReorderable())
        {
            PushButton upButton = getUpButton(index);
            upButton.addClickHandler(new ClickHandler()
            {
                public void onClick(ClickEvent event)
                {
                    _rows.set(index, _rows.get(index - 1));
                    _rows.set(index - 1, rowObject);
                    fireChangeEvent();
                    refresh();
                }
            });
            upButton.setEnabled(index > 0);
            Tooltip.addTooltip(upButton, "Click to move up");
            _table.setWidget(tableRow, col++, upButton);

            PushButton downButton = getDownButton(index);
            downButton.addClickHandler(new ClickHandler()
            {
                public void onClick(ClickEvent event)
                {
                    _rows.set(index, _rows.get(index + 1));
                    _rows.set(index + 1, rowObject);
                    fireChangeEvent();
                    refresh();
                }
            });
            downButton.setEnabled(index < _rows.size() - 1);
            Tooltip.addTooltip(downButton, "Click to move down");
            _table.setWidget(tableRow, col++, downButton);
        }
        else
        {
            _table.setText(tableRow, col++, "");
            _table.setText(tableRow, col++, "");
        }


        if (!readOnly && canDelete(rowObject))
        {
            if (status == FieldStatus.Deleted)
            {
                PushButton cancelButton = getCancelButton(index, new ClickHandler()
                {
                    public void onClick(ClickEvent event)
                    {
                        markUndeleted(index);
                    }
                });
                Tooltip.addTooltip(cancelButton, "Click to cancel deletion");
                _table.setWidget(tableRow,col,cancelButton);
            }
            else
            {
                PushButton deleteButton = getDeleteButton(index, new ClickHandler()
                {
                    public void onClick(ClickEvent event)
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
                            if (!_warnedAboutDelete)
                            {
                                // If we haven't already warned about the dangers of delete, do so now
                                ImageButton okButton = new ImageButton("OK", new ClickHandler()
                                {
                                    public void onClick(ClickEvent e)
                                    {
                                        // Once they say yes, don't bother them again
                                        _warnedAboutDelete = true;
                                        markDeleted(index);
                                    }
                                });

                                WindowUtil.showConfirmDialog("Confirm Field Deletion",
                                        "Are you sure you want to remove this field? All of its data will be deleted as well.",
                                        okButton);
                            }
                            else
                            {
                                // Otherwise, don't bother the user again
                                markDeleted(index);
                            }
                        }
                    }
                });
                Tooltip.addTooltip(deleteButton, "Click to delete");
                _table.setWidget(tableRow,col,deleteButton);
            }
        }
        else
            _table.setText(tableRow,col,"");
        col++;


        {
        Image decorationImage = getDecorationImage(status, rowObject);
        if (null == decorationImage)
            _table.setText(tableRow, col++, "");
        else
            _table.setWidget(tableRow, col++, decorationImage);
        }


        RowWidgetListener listener = new RowWidgetListener(pd,index);

//        TextBox textbox = new _TextBox();
//        textbox.setName("ff_textbox" + index);
//        textbox.addFocusHandler(listener);
//        textbox.addKeyPressHandler(listener);
//        textbox.addChangeHandler(new ChangeHandler(){
//            public void onChange(ChangeEvent event)
//            {
//                _log("onChange(ff_textbox)");
//            }
//        });
//        _table.setWidget(tableRow,col++,textbox);


        Widget name;
        if (readOnly || !isNameEditable(rowObject))
        {
            name = new Label(pd.getName());
            name.setHeight("20px");
        }
        else
        {
            BoundTextBox nameTextBox = new BoundTextBox(pd, pd.bindProperty("name"), "120", 200, prefixInputId + "name" + index, "ff_name" + index);
            nameTextBox.setValidator(new ColumnNameValidator());
            nameTextBox.addListener(Events.Focus, listener);
            nameTextBox.addListener(Events.KeyPress, listener);
            //nameTextBox.setEnabled(isNameEditable(rowObject));
            name = nameTextBox;
        }
        _table.setWidget(tableRow, col, name);

        col++;

        Widget label;
        if (readOnly)
        {
            label = new Label(pd.getLabel());
        }
        else
        {
            BoundTextBox labelTextBox = new BoundTextBox(pd, pd.bindProperty("label"), "120", 200, prefixInputId + "label" + index, "ff_label" + index);
            labelTextBox.addListener(Events.Focus, listener);
            labelTextBox.addListener(Events.KeyPress, listener);
            labelTextBox.setEnabled(!readOnly);
            label = labelTextBox;
        }
        _table.setWidget(tableRow, col, label);
        col++;

        Widget type;
        if (readOnly || !isTypeEditable(rowObject))
        {
            if (useConceptPicker)
            {
                type = new Label(ConceptPicker.getDisplayString(pd));
            }
            else
            {
                type = new Label(TypePicker.getDisplayString(pd.getRangeURI()));
            }
        }
        else
        {
            if (useConceptPicker)
            {
                ConceptPicker picker = new ConceptPicker.Bound(_lookupService, "ff_type" + index, pd);
                picker.addListener(Events.Focus, listener);
                picker.addListener(Events.KeyPress, listener);
                picker.addListener(Events.Change, new Listener<FieldEvent>()
                {
                    public void handleEvent(FieldEvent be)
                    {
                        // UNDONE: this is a terrible place to put this call.  ConceptPicker.Bound updates the type of
                        // the underlying property descriptor in a change listener like this one, so updating measure
                        // and dimension here introduces a dependency on listener order.
                        pd.guessMeasureAndDimension();
                        fireChangeEvent();
                    }
                });
                picker.setAllowAttachmentProperties(_domain.isAllowAttachmentProperties());
                picker.setAllowFileLinkProperties(_domain.isAllowFileLinkProperties());
                // distinguish between RangeEditable and any ConceptEditable
                picker.setIsRangeEditable(isRangeEditable(rowObject));
                type = picker;
            }
            else
            {
                BoundTypePicker typePicker = new BoundTypePicker(pd, "ff_type" + index, _domain.isAllowFileLinkProperties(), _domain.isAllowAttachmentProperties());
                typePicker.addFocusHandler(listener);
                typePicker.setRangeURI(pd.getRangeURI());
                typePicker.setEnabled(isRangeEditable(rowObject));
                type = typePicker;
            }
        }
        _table.setWidget(tableRow, col, type);
        col++;

        if (!useConceptPicker)
        {
            if (!readOnly)
            {
                PushButton l = getDownButton("lookup" + index, new ClickHandler()
                {
                    public void onClick(ClickEvent sender)
                    {
                        select(pd);
                        editLookup(index);
                   }
                });
                Tooltip.addTooltip(l, "Click to edit the lookup");
                _table.setWidget(tableRow,col,l);
            }
            else
            {
                _table.setText(tableRow,col,"");
            }
            col++;
            _table.setText(tableRow,col,pd.getLookupDescription());
            col++;
        }

        // blank cell
        _table.setHTML(tableRow, col,"&nbsp");
        formatter.setWidth(tableRow, col, "900");
        formatter.setHeight(tableRow, col, "22");
    }

    static PushButton getImageButton(String action, Object idSuffix, ClickHandler h)
    {
        String src = PropertyUtil.getContextPath() + "/_images/" + action + ".gif";
        PushButton result = new PushButton(new Image(src));
        result.setTabIndex(-1);
        String id = action + "_" + idSuffix;
        result.getElement().setId(id);
        if (null != h)
            result.addClickHandler(h);
        return result;
    }

    PushButton getUpButton(Object idSuffix)
    {
        return getImageButton("partup", idSuffix, null);
    }

    public static PushButton getDownButton(Object idSuffix)
    {
        return getDownButton(idSuffix, null);
    }

    public static PushButton getDownButton(Object idSuffix, ClickHandler handler)
    {
        return getImageButton("partdown", idSuffix, handler);
    }

    PushButton getDeleteButton(Object idSuffix, ClickHandler l)
    {
        return getImageButton("partdelete", idSuffix, l);
    }

    PushButton getCancelButton(Object idSuffix, ClickHandler l)
    {
        return getImageButton("cancel", idSuffix, l);
    }

    protected Image getStatusImage(String id, FieldStatus status, Row row)
    {
        String src = PropertyUtil.getContextPath() + "/_images/part" + status.toString().toLowerCase() + ".gif";
        Image i = new Image(src);
        DOM.setElementProperty(i.getElement(), "id", id);
        Tooltip.addTooltip(i, status.getDescription());
        return i;
    }

    protected Image getDecorationImage(FieldStatus status, Row row)
    {
        return null;
    }


    Row getRow(int i)
    {
        return _rows.get(i);
    }


    protected boolean canDelete(Row row)
    {
        return null == row.orig || !_domain.isMandatoryField(row.orig);
    }


    protected boolean isTypeEditable(Row row)
    {
        return null == row.orig || !_domain.isMandatoryField(row.orig);
    }


    protected boolean isRangeEditable(Row row)
    {
        if (!isTypeEditable(row))
            return false;
        FieldStatus status = getStatus(row);
        return status == FieldStatus.Added;
    }


    protected boolean isNameEditable(Row row)
    {
        return null == row.orig || !_domain.isMandatoryField(row.orig);            
    }


    /** @return ReadOnly status of the field. */
    public boolean isReadOnly(Row row)
    {
        FieldType pd = row.edit;

        if (_readOnly || !_domain.isEditable(pd))
            return true;

        if (FieldStatus.Deleted == getStatus(row))
            return true;

        // new properties (no container) or those in the domain's container
        boolean inSameContainer = pd != null && (pd.getContainer() == null || pd.getContainer().equals(_domain.getContainer()));
        return !inSameContainer;
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


    public FieldStatus getStatus(Row row)
    {
        if (row.deleted)
            return FieldStatus.Deleted;

        GWTPropertyDescriptor orig = row.orig;
        GWTPropertyDescriptor edit = row.edit;

        if (orig == null)
            return FieldStatus.Added;

        return edit.equals(orig) ? FieldStatus.Existing : FieldStatus.Changed;
    }


    public boolean isDeleted(int i)
    {
        return getRow(i).deleted;
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

            if (!PropertiesEditorUtil.isLegalName(name))
            {
                errors.add(BAD_NAME_ERROR_MESSAGE);
                continue;
            }

            names.add(name.toLowerCase());
        }
        return new ArrayList<String>(errors);
    }


    public DomainType getUpdates()
    {
        for (PropertyPane<DomainType, FieldType> propertiesPane : _propertiesPanes)
        {
            propertiesPane.copyValuesToPropertyDescriptor();
        }
        DomainType d = _domain; // UNDONE COPY
        ArrayList<FieldType> l = new ArrayList<FieldType>();
        for (int i=0 ; i<_rows.size() ; i++)
        {
            Row r = getRow(i);
            FieldStatus status = getStatus(r.edit);
            if (status == FieldStatus.Deleted)
                continue;
            r.edit.setName(_trimToNull(r.edit.getName()));
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

    public FieldType getOriginalPropertyDescriptor(int i)
    {
        return getRow(i).orig;
    }

    public int getPropertyCount()
    {
        return null == _rows ? 0 : _rows.size();
    }

    public int getPropertyCount(boolean includeDeleted)
    {
        if (includeDeleted || null == _rows)
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
        if (_readOnly == readOnly)
            return;
        _readOnly = readOnly;
        refreshButtons(_buttonPanel);
        refresh();
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

    public void addChangeHandler(ChangeHandler ch)
    {
        _listeners.add(ch);
    }

    private void fireChangeEvent()
    {
        for (ChangeHandler listener : _listeners)
            listener.onChange(null);
    }

    private interface WarningValidator extends Validator
    {
        public String warning(Field<?> field, String value);
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
        FieldType _p;

        BoundTypePicker(FieldType pd, String id, boolean allowFileLinkProperties, boolean allowAttachmentProperties)
        {
            super(pd.getRangeURI(), allowFileLinkProperties, allowAttachmentProperties);
            _p = pd;
            DOM.setElementProperty(getElement(), "id", id);
            addChangeListener(this);
        }

        public void onChange(Widget sender)
        {
            _p.setRangeURI(getRangeURI());
            _p.setFormat(null);
            _p.guessMeasureAndDimension();
            refreshRow(_p);
        }
    }



    private class _TextField<D> extends TextField<D>
    {
        public _TextField()
        {
            sinkEvents(Event.ONCHANGE);
        }

        public void onComponentEvent(ComponentEvent ce)
        {
            super.onComponentEvent(ce);
            if (ce.getEventTypeInt() == Event.ONCHANGE)
            {
                onChange(ce);
            }
        }

        // TODO avoid double fireChangeEvent() on blur
        protected void onChange(ComponentEvent be)
        {
            D v = getValue();
            value = v;
            fireChangeEvent(focusValue, v);
        }
    }



    private class BoundTextBox extends _TextField<String> implements BoundWidget
    {
        FieldType _pd = null;
        IPropertyWrapper _prop;

        private BoundTextBox(String width, int maxLength, String id, String name)
        {
            if (null != id)
                setId(id);
            if (null != name)
                setName(name);
            if (maxLength > 0)
                setMaxLength(maxLength);
            if (width != null && width.length()!=0)
                setWidth(width);
            setAutoValidate(true);

            this.addListener(Events.Change, new Listener<ComponentEvent>(){
                public void handleEvent(ComponentEvent be)
                {
                    _log("Listener.handleEvent(Events.Change, " + getName());
                    pushChange();
                }
            });
        }


        BoundTextBox(FieldType pd, IPropertyWrapper prop, String width, int maxLength, String id, String name)
        {
            this(width, maxLength, id, name);
            _pd = pd;
            _prop = prop;
            _log("init " + getName() + " = " + _prop.get());
            pullValue();
        }


        /* we do our own UI for error and warning so always return true */
        @Override
        public boolean validateValue(String text)
        {
            _log("validate(" + text + ")");
            String error = updateErrorFormat(text, false);
            return true;
        }


        /* push data from field to bound property, refreshRow() on status change */
        void pushChange()
        {                 
            _log("update()  " + getName() + "=" + getValue() + ", " + getRawValue() +", " + (null==getInputEl()?"???":getInputEl().getValue()));

            String text = _default(getValue(), null);

            if (null != updateErrorFormat(text, false))
                return;

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
                }
            }
            else
            {
                _prop.set(text);
            }
        }


        public void pushValue()
        {
            String text = _default(getValue(), null);
            _prop.set(text);
        }


        public void pullValue()
        {
            String text = _default((String)_prop.get(),"");
            if (!text.equals(getValue()))
                setValue(text);
        }


        @Override
        public void clearInvalid()
        {
        }


        String updateErrorFormat(String text, boolean alert)
        {
            if (null == getValidator() || !isRendered())
                return null;
            text = _trimToNull(text);
            String message = getValidator().validate(this, text);
            if (null != message)
            {
                getInputEl().addStyleName("labkey-textbox-error");
                getInputEl().setTitle(message);
                if (alert)
                    Window.alert(message);
                return message;
            }
            else
            {
                // No error. How about a warning?
                String warning = (getValidator() instanceof WarningValidator) ? ((WarningValidator)getValidator()).warning(this, text) : null;
                if (null != warning)
                {
                    getInputEl().addStyleName("labkey-textbox-warning");
                    getInputEl().setTitle(warning);
                    return null; // Never alert
                }
            }
            getInputEl().removeStyleName("labkey-textbox-error", "labkey-textbox-warning");
            getInputEl().setTitle("");
            return null;
        }
    }


    public static class _TextBox extends TextBox
    {
        @Override
        public void onBrowserEvent(Event event)
        {
            _log("TextBox.onBrowserEvent(" + event.getType() + ")");
            super.onBrowserEvent(event);
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
        return new LookupEditor<FieldType>(_lookupService, new LookupListener<FieldType>()
        {
            public void lookupUpdated(FieldType pd)
            {
                int row = getRow(pd);
                if (row != -1)
                {
                    refreshRow(pd);
                }
            }
        }, true);
    }

    public void addButton(ImageButton imageButton)
    {
        _buttonPanel.add(imageButton);
    }


    private static boolean nullEquals(Object s1, Object s2)
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

    public static void _log(String s)
    {
//        _logConsole(s);
//        _logGwtDebug(s);
    }

    private static native void _logConsole(String s) /*-{
        console.log(s);
    }-*/;

    private static void _logGwtDebug(String s)
    {
        Element e = DOM.getElementById("gwtDebug");
        if (null != e)
        {
            String v = e.getInnerText();
            String append = (new Date()) + "    " + s + "\n";
            e.setInnerText(v + append);
            e.setScrollTop(e.getScrollHeight());
        }
    }


    private static boolean _empty(String s) {return null==s || s.length()==0;}
    private static String _string(Object o) {return null==o ? "" : o.toString();}
    private static String _default(String a, String b) {return _empty(a) ? b : a;}
    private static String _trimToNull(String a) {return _empty(a) ? null : StringUtils.trimToNull(a.toString());}
}
