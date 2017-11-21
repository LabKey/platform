/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import com.extjs.gxt.ui.client.event.ComponentEvent;
import com.extjs.gxt.ui.client.event.Events;
import com.extjs.gxt.ui.client.event.FieldEvent;
import com.extjs.gxt.ui.client.event.Listener;
import com.extjs.gxt.ui.client.event.TabPanelEvent;
import com.extjs.gxt.ui.client.util.Size;
import com.extjs.gxt.ui.client.widget.TabItem;
import com.extjs.gxt.ui.client.widget.form.Field;
import com.extjs.gxt.ui.client.widget.form.TextField;
import com.extjs.gxt.ui.client.widget.form.Validator;
import com.extjs.gxt.ui.client.widget.layout.FitLayout;
import com.google.gwt.dom.client.Node;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.HasAllMouseHandlers;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.ChangeListener;
import com.google.gwt.user.client.ui.DockPanel;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLTable;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.PushButton;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.property.ConditionalFormatItem;
import org.labkey.api.gwt.client.ui.property.DefaultScaleItem;
import org.labkey.api.gwt.client.ui.property.DefaultValueItem;
import org.labkey.api.gwt.client.ui.property.DescriptionItem;
import org.labkey.api.gwt.client.ui.property.DimensionItem;
import org.labkey.api.gwt.client.ui.property.ExcludeFromShiftingItem;
import org.labkey.api.gwt.client.ui.property.FacetingBehaviorItem;
import org.labkey.api.gwt.client.ui.property.FormatItem;
import org.labkey.api.gwt.client.ui.property.ImportAliasesItem;
import org.labkey.api.gwt.client.ui.property.MaxLengthItem;
import org.labkey.api.gwt.client.ui.property.MeasureItem;
import org.labkey.api.gwt.client.ui.property.MvEnabledItem;
import org.labkey.api.gwt.client.ui.property.PHIItem;
import org.labkey.api.gwt.client.ui.property.RecommendedVariableItem;
import org.labkey.api.gwt.client.ui.property.RequiredItem;
import org.labkey.api.gwt.client.ui.property.URLItem;
import org.labkey.api.gwt.client.ui.property.ValidatorItem;
import org.labkey.api.gwt.client.ui.property.VisibilityItem;
import org.labkey.api.gwt.client.util.IPropertyWrapper;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: matthewb
 * Date: Apr 24, 2007
 * Time: 2:10:20 PM
 */
public class PropertiesEditor<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> implements DomainProvider
{
    public static class PD extends PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor>
    {
        public PD(RootPanel rootPanel, Saveable<GWTDomain> owner, LookupServiceAsync service)
        {
            super(rootPanel, owner, service, new GWTPropertyDescriptor());
        }

        public PD(RootPanel rootPanel, Saveable<GWTDomain> owner, LookupServiceAsync service, boolean alwaysAllowImportSchema)
        {
            super(rootPanel, owner, service, new GWTPropertyDescriptor(), alwaysAllowImportSchema);
        }
    }

    protected VerticalPanel _contentPanel;
    private com.extjs.gxt.ui.client.widget.TabPanel _extraPropertiesTabPanel = new com.extjs.gxt.ui.client.widget.TabPanel();
    private Image _spacerImage;
    protected boolean _warnAboutDelete = true;
    private static final String BAD_NAME_WARNING_MESSAGE = "To improve compatibility with SQL queries, R scripts, and other code, consider using field names that only contain letters, numbers, and underscores (_), and start with a letter or underscore.";

    public enum FieldStatus
    {
        Added("This field is newly added", "fa-plus-circle"),
        Deleted("This field is marked for deletion", "fa-trash-o"),
        Existing("This field has not been changed", null),
        Changed("This field has been edited", "fa-wrench");

        private final String _description;
        private final String _fontClass;

        FieldStatus(String description, String fontClass)
        {
            _description = description;
            _fontClass = fontClass;
        }

        public String getDescription()
        {
            return _description;
        }

        public String getClassName()
        {
            String cls = "labkey-link gwt-FontImage";
            if (_fontClass != null)
                cls += " fa " + _fontClass;
            return cls;
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
    private boolean _alwaysAllowImportSchema = false;
    private ImageButton _exportSchemaButton;
    private ImageButton _inferSchemaButton;
    private ImageButton _compareTemplateButton;
    private DefaultValueItem<DomainType, FieldType> _defaultValueSelector;
    protected PHIItem<DomainType, FieldType> _phiSelector;

    protected DomainType _domain;
    ArrayList<Row> _rows;
    private final RootPanel _rootPanel;
    FieldType _newPropertyDescriptor;

    String _schemaName = null;
    String _queryName = null;

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

    public PropertiesEditor(RootPanel rootPanel, Saveable<GWTDomain> owner, LookupServiceAsync service, FieldType empty)
    {
        this(rootPanel, owner, service, empty, false);
    }

    public PropertiesEditor(RootPanel rootPanel, Saveable<GWTDomain> owner, LookupServiceAsync service, FieldType empty, boolean alwaysAllowImportSchema)
    {
        _rootPanel = rootPanel;
        _newPropertyDescriptor = empty;
        _rows = new ArrayList<Row>();

        _lookupService = new CachingLookupService(service);
        _owner = owner;

        _alwaysAllowImportSchema = alwaysAllowImportSchema;

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
                if (null == cell)
                    return;
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
        _buttonPanel.getElement().setClassName("gwt-ButtonBar");

        _addFieldButton = new ImageButton("Add Field", addListener);

        ClickHandler importSchemaListener = new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                final ImportSchemaWizard popup = new ImportSchemaWizard(PropertiesEditor.this);
                popup.setText("Import Fields");
                popup.show();
                WindowUtil.centerDialog(popup);
            }
        };

        ClickHandler exportSchemaListener = new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                final ExportSchemaWizard popup = new ExportSchemaWizard(PropertiesEditor.this);
                popup.setText("Export Fields");
                popup.show();
                WindowUtil.centerDialog(popup);
            }
        };

        ClickHandler inferSchemaListener = new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                final InferSchemaWizard popup = new InferSchemaWizard(PropertiesEditor.this);
                popup.setText("Infer Fields from File");
                popup.show();
                WindowUtil.centerDialog(popup);
            }
        };

        ClickHandler compareTemplateListener = new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                _navigate("./property-compareWithTemplate.view?schemaName=" + URL.decode(_schemaName) + "&queryName=" + URL.encode(_queryName));
            }
        };

        _importSchemaButton = new ImageButton("Import Fields", importSchemaListener);
        // Assume this button will be hidden; conditionally setVisible(true) in init.
        _importSchemaButton.setVisible(false);
        _exportSchemaButton = new ImageButton("Export Fields", exportSchemaListener);
        // Visibility rules for InferSchema button are the same as ImportSchema
        _inferSchemaButton = new ImageButton("Infer Fields from File", inferSchemaListener);
        _inferSchemaButton.setVisible(false);

        _compareTemplateButton = new ImageButton("Compare with template", compareTemplateListener);
        _compareTemplateButton.setVisible(false);

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
        _extraPropertiesTabPanel.setPixelSize(460, getExtraPropertiesHeight());
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
        propertyDock.addStyleName("editor-property-dock");

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
        _table.getFlexCellFormatter().setWidth(0, col, "200px");
        setBoldText(_table, 0, col++, "Name");
        _table.getFlexCellFormatter().setWidth(0, col, "120px");
        setBoldText(_table, 0, col++, "Label");
        _table.getFlexCellFormatter().setWidth(0, col, "155px");
        setBoldText(_table, 0, col++, "Type");

        _readOnly = false;
        refreshButtons(_buttonPanel);
    }


    public void setSchemaName(String schemaName)
    {
        this._schemaName = schemaName;
    }

    public void setQueryName(String queryName)
    {
        this._queryName = queryName;
    }

    protected int getExtraPropertiesHeight()
    {
        return 260;
    }

    protected List<PropertyPane<DomainType, FieldType>> createPropertyPanes(DockPanel propertyDock)
    {
        PropertyPane<DomainType, FieldType> displayPane = new PropertyPane<DomainType, FieldType>(this, "Display");
        displayPane.addItem(new DescriptionItem<DomainType, FieldType>(displayPane));
        displayPane.addItem(new URLItem<DomainType, FieldType>(displayPane));
        displayPane.addItem(new VisibilityItem<DomainType, FieldType>(displayPane));
        addChangeHandler(displayPane.getChangeListener());
        addChangeHandler(new ChangeHandler()
        {
            public void onChange(ChangeEvent event)
            {
                if (_selectedPD != null)
                {
                    updateStatusImage(_selectedPD);
                }
            }
        });

        PropertyPane<DomainType, FieldType> formatPane = new PropertyPane<DomainType, FieldType>(this, "Format");
        formatPane.addItem(new FormatItem<DomainType, FieldType>(formatPane));
        formatPane.addItem(new ConditionalFormatItem<DomainType, FieldType>(getRootPanel(), formatPane));
        addChangeHandler(formatPane.getChangeListener());

        PropertyPane<DomainType, FieldType> validatorPane = new PropertyPane<DomainType, FieldType>(this, "Validators");
        validatorPane.addItem(new RequiredItem<DomainType, FieldType>(validatorPane));
        validatorPane.addItem(new ValidatorItem<DomainType, FieldType>(validatorPane));
        addChangeHandler(validatorPane.getChangeListener());

        PropertyPane<DomainType, FieldType> reportingPane = new PropertyPane<DomainType, FieldType>(this, "Reporting");
        reportingPane.addItem(new MeasureItem<DomainType, FieldType>(reportingPane));
        reportingPane.addItem(new DimensionItem<DomainType, FieldType>(reportingPane));
        reportingPane.addItem(new RecommendedVariableItem<DomainType, FieldType>(reportingPane));
        reportingPane.addItem(new DefaultScaleItem<DomainType, FieldType>(reportingPane));
        addChangeHandler(reportingPane.getChangeListener());

        PropertyPane<DomainType, FieldType> advancedPane = new PropertyPane<DomainType, FieldType>(this, "Advanced");
        advancedPane.addItem(new MvEnabledItem<DomainType, FieldType>(advancedPane));
        _defaultValueSelector = new DefaultValueItem<DomainType, FieldType>(_owner, advancedPane);
        advancedPane.addItem(_defaultValueSelector);
        advancedPane.addItem(new ImportAliasesItem<DomainType, FieldType>(advancedPane));
        _phiSelector = new PHIItem<DomainType, FieldType>(advancedPane);
        advancedPane.addItem(_phiSelector);
        advancedPane.addItem(new ExcludeFromShiftingItem<DomainType, FieldType>(advancedPane));
        advancedPane.addItem(new FacetingBehaviorItem<DomainType, FieldType>(advancedPane));
        advancedPane.addItem(new MaxLengthItem<DomainType, FieldType>(advancedPane));
        addChangeHandler(advancedPane.getChangeListener());

        List<PropertyPane<DomainType, FieldType>> result = new ArrayList<PropertyPane<DomainType, FieldType>>();
        result.add(displayPane);
        result.add(formatPane);
        result.add(validatorPane);
        result.add(reportingPane);
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

        if (null == _schemaName && null != domain.getSchemaName())
            _schemaName = domain.getSchemaName();
        if (null == _queryName && null != domain.getQueryName())
            _queryName = domain.getQueryName();

        List<FieldType> fields = domain.getFields();
        if (null != fields)
        {
            for (FieldType field : fields)
            {
                // we assume that propertyId==0 usually means this is a new field
                // however, assay round trips uncreated mandatory fields through the editor, so mark them as not-new
                if (domain.isMandatoryField(field) && field.getPropertyId() == 0)
                    field.setPropertyId(-1);

                Row row = new Row(field);
                _rows.add(row);
            }
        }

        // Certain provisioned tables (Lists and Datasets) always get the Import/Infer Fields buttons. All others only get the
        // button on initial creation.
        if (_alwaysAllowImportSchema || domain.getDomainId() == 0)
        {
            _importSchemaButton.setVisible(true);
            _inferSchemaButton.setVisible(true);
        }
        //if (domain.getTemplateInfo() != null)
        if (null != domain.getTemplateDescription() && null != _schemaName && null != _queryName)
        {
            _compareTemplateButton.setEnabled(!isDirty());
            _compareTemplateButton.setVisible(true);
        }

        fireChangeEvent();

        refresh();
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
        index = moveNewFieldToBelowSelectedField(index);
        select(newRow.edit);
        setFocus(index);
        return newRow.edit;
    }

    private int moveNewFieldToBelowSelectedField(int index)
    {
        if (_selectedPD != null)
        {
            int targetRow = getRow(_selectedPD);
            while (index > targetRow + 1)
            {
                Row moveUp = _rows.get(index);
                Row moveDown = _rows.get(index - 1);
                if (! isShiftable(moveDown))  // abort if a row is not shiftable (i.e. static rows stay at the top)
                    break;
                _rows.set(index, moveDown);
                _rows.set(index - 1, moveUp);
                fireChangeEvent();
                refreshRow(index, moveDown);
                refreshRow(index-1, moveUp);
                index--;
            }
        }
        return index;
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
        catch (Exception ignored)
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

    public FieldType getProperty(String name)
    {
        for (Row row : _rows)
        {
            if (row.edit.getName().equalsIgnoreCase(name))
            {
                return row.edit;
            }
        }
        return null;
    }

    public void refresh()
    {
        while (_table.getRowCount() >= _rows.size() + 2)
            _table.removeRow(_table.getRowCount() - 1);

        _table.setVisible(!_rows.isEmpty());
        _noColumnsPanel.setVisible(_rows.isEmpty());

        for (int index=0 ; index < _rows.size() ; index++)
        {
            Row row = _rows.get(index);
            refreshRow(index, row);
        }

        select(_selectedPD, true);
    }
    

    protected void refreshButtons(HorizontalPanel buttonPanel)
    {
        if (_readOnly)
        {
            buttonPanel.clear();
            buttonPanel.add(_exportSchemaButton);
        }
        else
        {

            buttonPanel.clear();
            buttonPanel.add(_addFieldButton);
            buttonPanel.add(_importSchemaButton);
            buttonPanel.add(_exportSchemaButton);
            buttonPanel.add(_inferSchemaButton);
        }
        buttonPanel.add(_compareTemplateButton);
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
                e.removeClassName("selected-field-row");
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
                _defaultValueSelector.setEnabled(!readOnly && _domain.getDefaultValueOptions().length > 0);
            }
            if (_phiSelector != null)
            {
                _phiSelector.setEnabled(!readOnly && _domain.isProvisioned());  // only support PHI metadata selection for provisioned tables
                                                                                // TODO: this is only a rough check, should restrict to only tables we support PHI for explicitly
                                                                                // TODO: should also always disable in schema browser
            }

            Element e = _table.getRowFormatter().getElement(tableRow);
            e.addClassName("selected-field-row");

            if (isPropertiesEditable(getRow(index)))
            {
                _extraPropertiesTabPanel.setVisible(true);
                repositionExtraProperties();
            }
            else
            {
                _extraPropertiesTabPanel.setVisible(false);
            }
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

        public void onFocus(FocusEvent event)       // FocusHandler (gwt)
        {
            focus();
        }

        public void handleEvent(ComponentEvent e)   // Listener(Events.KeyPress,Events.Focus) (gxt)
        {
            if (e.getType() ==  Events.KeyPress)
                componentKeyPress(e);
            else if (e.getType() == Events.Focus)
                focus();
        }

        public void onKeyPress(KeyPressEvent event) // KeyPressHandler (gwt)
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
            if (null != value && _domain.getReservedFieldNames().contains(value.toLowerCase()))
                return "'" + value + "' is reserved";
            return null;
        }

        public String warning(Field<?> field, String text)
        {
            if (text == null || text.isEmpty())
                return null;
            return validateFieldName(text);
        }
    }

    /** @return null if the field name has no questionable characters or the error string otherwise. */
    protected String validateFieldName(String value)
    {
        if (value == null || (PropertiesEditorUtil.isLegalName(value) && !value.contains(" ")))
            return null;
        return BAD_NAME_WARNING_MESSAGE;
    }

    public void refreshRow(final int index, final Row rowObject)
    {
        HTMLTable.CellFormatter formatter = _table.getCellFormatter();
        int tableRow = index+1;

        _table.getRowFormatter().setStylePrimaryName(tableRow, "editor-field-row");
        int cellCount = _table.getCellCount(0);
        for (int c=0 ; c<cellCount ; c++)
            formatter.setHeight(tableRow, c, "23");

        int col = 0;
        final FieldType pd = rowObject.edit;
        FieldStatus status = getStatus(rowObject);
        boolean readOnly = isReadOnly(rowObject);
        pd.setTypeEditable(isTypeEditable(rowObject));

        String imageId = "partstatus_" + index;
        HTML statusImage = getStatusHtml(imageId, status);
        if (status != FieldStatus.Existing)
            fireChangeEvent();
        _table.setWidget(tableRow, col, statusImage);
        col++;

        if (isReorderable())
        {
            FontButton upButton = getUpButton(index);
            upButton.addClickHandler(new ClickHandler()
            {
                public void onClick(ClickEvent event)
                {
                    if (index < 1)
                        return;
                    Row moveUp = rowObject;
                    Row moveDown = _rows.get(index - 1);
                    _rows.set(index, moveDown);
                    _rows.set(index - 1, moveUp);
                    fireChangeEvent();
                    refreshRow(index, moveDown);
                    refreshRow(index-1, moveUp);
                }
            });
            if (index > 0)
            {
                Row prev = _rows.get(index - 1);
                upButton.setEnabled(isShiftable(rowObject) && isShiftable(prev));
            }
            else
            {
                upButton.setEnabled(false);
            }
            addTooltip(upButton, "Click to move up");
            _table.setWidget(tableRow, col++, upButton);

            FontButton downButton = getDownButton(index);
            downButton.addClickHandler(new ClickHandler()
            {
                public void onClick(ClickEvent event)
                {
                    if (index > _rows.size() - 2)
                        return;
                    Row moveDown = rowObject;
                    Row moveUp = _rows.get(index + 1);
                    _rows.set(index, moveUp);
                    _rows.set(index + 1, moveDown);
                    fireChangeEvent();
                    refreshRow(index, moveUp);
                    refreshRow(index + 1, moveDown);
                }
            });
            if (index < _rows.size() - 1)
            {
                Row next = _rows.get(index + 1);
                downButton.setEnabled(isShiftable(rowObject) && isShiftable(next));
            }
            else
            {
                downButton.setEnabled(false);
            }
            addTooltip(downButton, "Click to move down");
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
                addTooltip(cancelButton, "Click to cancel deletion");
                _table.setWidget(tableRow,col,cancelButton);
            }
            else
            {
                FontButton deleteButton = getDeleteButton(index, new ClickHandler()
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
                            if (_warnAboutDelete)
                            {
                                // If we haven't already warned about the dangers of delete, do so now
                                ImageButton okButton = new ImageButton("OK", new ClickHandler()
                                {
                                    public void onClick(ClickEvent e)
                                    {
                                        // Once they say yes, don't bother them again
                                        _warnAboutDelete = false;
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
                addTooltip(deleteButton, "Click to delete");
                _table.setWidget(tableRow,col,deleteButton);
            }
        }
        else
            _table.setText(tableRow,col,"");
        col++;


        {
        Widget decorationImage = getDecorationImage(status, rowObject);
        if (null == decorationImage)
            _table.setText(tableRow, col++, "");
        else
            _table.setWidget(tableRow, col++, decorationImage);
        }


        RowWidgetListener listener = new RowWidgetListener(pd,index);

        Widget name;
        if (readOnly || !isNameEditable(rowObject))
        {
            name = new Label(pd.getName());
            ((Label)name).setWordWrap(false);
            name.setWidth("200px");
            name.setHeight("20px");
        }
        else
        {
            BoundTextBox nameTextBox = new BoundTextBox(pd, pd.bindProperty("name"), "120", 200, prefixInputId + "name" + index, "ff_name" + index);
            nameTextBox.setValidator(new ColumnNameValidator());
            nameTextBox.addListener(Events.Focus, listener);
            nameTextBox.addListener(Events.KeyPress, listener);
            name = nameTextBox;
        }
        _table.setWidget(tableRow, col, name);

        col++;

        Widget label;
        if (readOnly || !isLabelEditable(rowObject))
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
            type = new Label(ConceptPicker.getDisplayString(pd));
        }
        else
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
            picker.setAllowFlagProperties(_domain.isAllowFlagProperties());
            // distinguish between RangeEditable and any ConceptEditable
            picker.setIsRangeEditable(isRangeEditable(rowObject));
            type = picker;
        }
        _table.setWidget(tableRow, col, type);
        col++;

        // blank cell
        _table.setHTML(tableRow, col,"&nbsp;");
        formatter.setWidth(tableRow, col, "900");
        formatter.setHeight(tableRow, col, "23");
    }

    public static PushButton getImageButton(String action, Object idSuffix, ClickHandler h)
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

    public static FontButton getFontButton(String fontClass, String action, Object idSuffix, ClickHandler h)
    {
        FontButton button = new FontButton(fontClass);

        String id = action + "_" + idSuffix;
        button.getElement().setId(id);
        if (null != h)
            button.addClickHandler(h);
        return button;
    }

    FontButton getUpButton(Object idSuffix)
    {
        return getFontButton("fa-caret-square-o-up", "partup", idSuffix, null);
    }

    public static FontButton getDownButton(Object idSuffix)
    {
        return getDownButton(idSuffix, null);
    }

    public static FontButton getDownButton(Object idSuffix, ClickHandler handler)
    {
        return getFontButton("fa-caret-square-o-down", "partdown", idSuffix, handler);
    }

    public static FontButton getDeleteButton(Object idSuffix, ClickHandler l)
    {
        return getFontButton("fa-times", "partdelete", idSuffix, l);
    }

    PushButton getCancelButton(Object idSuffix, ClickHandler l)
    {
        return getImageButton("cancel", idSuffix, l);
    }

    protected HTML getStatusHtml(String id, FieldStatus status)
    {
        String fontClass = status.getClassName();
        HTML html = new HTML("<span class='" + fontClass + "'></span>");

        DOM.setElementProperty(html.getElement(), "id", id);
        addTooltip(html, status.getDescription());

        return html;
    }

    protected Widget getDecorationImage(FieldStatus status, Row row)
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


    /**
     *  true  : "type/concept/lookup" may be editable (except see isRangeEditable()) 
     *  false : "type/concept/lookup" are not editable
     **/
    protected boolean isTypeEditable(Row row)
    {
        return null == row.orig || !_domain.isMandatoryField(row.orig);
    }


    /**
     * true : the underlying storage type of the column may be changed.
     * false : the underlying storage type may not be changed
     */
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

    protected boolean isLabelEditable(Row row)
    {
        return !row.edit.getDisableEditing();
    }

    protected boolean isShiftable(Row row)
    {
        return !row.edit.getPreventReordering();
    }

    protected boolean isPropertiesEditable(Row row)
    {
        return !row.edit.getDisableEditing();
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

            if (lowerCaseReservedNames.contains(name.toLowerCase()) && p.getPropertyId() <= 0)
            {
                errors.add("\"" + name + "\" is a reserved field name in \"" + d.getName() + "\".");
                continue;
            }

            if (names.contains(name.toLowerCase()))
            {
                errors.add("All property names must be unique: " + name);
                continue;
            }

//            String nameError = validateFieldName(name);
//
//            if (nameError != null)
//            {
//                errors.add(nameError);
//                continue;
//            }

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
            if (!getRow(i).edit.equals(getRow(i).orig) || getRow(i).deleted)
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
        TestUtil.signalWebDriver("propertiesEditorChange");
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


    /** same as Tooltip except for override setPopupPosition()
     * NOTE: can't extend Tooltip since the constructor is private
     */
    public class FirefoxTooltip extends PopupPanel implements MouseOverHandler, MouseOutHandler
    {
        private Label _label;
        private Widget _sourceWidget;

        private FirefoxTooltip(String text, Widget sourceWidget)
        {
            super(true);
            _label = new Label(text);
            add(_label);
            setStyleName("gwt-ToolTip");
            _sourceWidget = sourceWidget;
        }

        public void onMouseOver(MouseOverEvent event)
        {
            show();
            int height = getOffsetHeight();
            int width = getOffsetHeight();
            int top = _sourceWidget.getAbsoluteTop() + height;
            int left = _sourceWidget.getAbsoluteLeft() + 13;
            int rightOverhang = left + width - (Window.getScrollLeft() + Window.getClientWidth());
            if (rightOverhang > 0)
            {
                left -= rightOverhang;
            }
            setPopupPosition(left, top);
        }

        public void onMouseOut(MouseOutEvent event)
        {
            hide();
        }

        public void setText(String text)
        {
            _label.setText(text);
        }

        // PopPanel constructor calls setPopupPosition(), which calls getBodyOffsetLeft() which is SLOW
        private boolean inConstructor = true;

        @Override
        public void setPopupPosition(int left, int top)
        {
            if (inConstructor)
            {
                inConstructor = false;
                if (0==left && 0==top)
                    return;
            }
            super.setPopupPosition(left, top);    //To change body of overridden methods use File | Settings | File Templates.
        }
    }


    void addTooltip(HasAllMouseHandlers widget, String text)
    {
        if (!isFirefox)
        {
            Tooltip.addTooltip(widget, text);
        }
        else
        {
            FirefoxTooltip tooltip = new FirefoxTooltip(text, (Widget)widget);
            widget.addMouseOverHandler(tooltip);
            widget.addMouseOutHandler(tooltip);
        }
    }


    void updateStatusImage(GWTPropertyDescriptor pd)
    {
        if (getRow(pd) != -1)
        {
            FieldStatus status = getStatus(pd);
            HTML html = (HTML)_table.getWidget(getRow(pd)+1,0);
            Element el = html.getElement();

            // have to dig out the span tag from parent div element
            if (el.getTagName().equalsIgnoreCase("div"))
            {
                Node node = el.getFirstChild();
                if (node.getNodeType() == Node.ELEMENT_NODE)
                {
                    el = (Element)node;
                }
            }

            if (!el.getNodeName().equalsIgnoreCase("span"))
                return;

            if (el.getClassName().trim().equalsIgnoreCase(status.getClassName()))
                return;

            if (status.getClassName() != null)
                el.setClassName(status.getClassName());
            else
                el.removeClassName(el.getClassName());

            addTooltip(html, status.getDescription());
            fireChangeEvent();
        }
    }


    private class _TextField<D> extends TextField<D>
    {
        public _TextField()
        {
            setHeight(22);
            // adjustSize==true causes getComputesStyle(), SLOW ON FIREFOX
            this.adjustSize = false;
            sinkEvents(Event.ONCHANGE);
        }

        @Override
        protected Size adjustInputSize()
        {
            // trying to make TextField look like the ConceptPicker (TriggerField)
            return new Size(0, isFirefox?4:2);
        }

        @Override
        protected void onResize(int width, int height)
        {
            if (errorIcon != null && errorIcon.isAttached())
            {
              alignErrorIcon();
            }
            Size asize = adjustInputSize();
            getInputEl().setSize(width - asize.width, height - asize.height, false);
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

    protected class BoundTextBox extends _TextField<String> implements BoundWidget
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

            this.addListener(Events.KeyUp, new Listener<ComponentEvent>(){
                public void handleEvent(ComponentEvent be)
                {
                    _log("Listener.handleEvent(Events.KeyUp, " + getName());
                    pushChange();
                }
            });
        }


        BoundTextBox(FieldType pd, IPropertyWrapper prop, String width, int maxLength, String id, String name)
        {
            this(width, maxLength, id, name);
            _pd = pd;
            _prop = prop;
            pullValue();
        }


        /* we do our own UI for error and warning so always return true */
        @Override
        public boolean validateValue(String text)
        {
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
                    // Just refresh the status icon, not the whole row
                    _prop.set(text);
                    if (status != getStatus(_pd))
                        updateStatusImage(_pd);
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


    public void addButton(ImageButton imageButton)
    {
        _buttonPanel.add(imageButton);
    }

    protected RootPanel getRootPanel()
    {
        return _rootPanel;
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

    public DomainType getDomain()
    {
        return _domain;
    }

    public static void _log(String s)
    {
        _logConsole(s);
        _logGwtDebug(s);
    }

    private static native void _logConsole(String s) /*-{
        if ('console' in window) window.console.log(s);
    }-*/;

    private static void _navigate(String url)
    {
        Window.Location.assign(url);
    }

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

    private static native boolean isFirefox() /*-{
        var ua = navigator.userAgent.toLowerCase();
        return !(/webkit/.test(ua)) && (/gecko/.test(ua));
    }-*/;

    private static final boolean isFirefox = isFirefox();

    private static boolean _empty(String s) {return null==s || s.length()==0;}
    private static String _string(Object o) {return null==o ? "" : o.toString();}
    private static String _default(String a, String b) {return _empty(a) ? b : a;}
    private static String _trimToNull(String a) {return _empty(a) ? null : StringUtils.trimToNull(a);}
}
