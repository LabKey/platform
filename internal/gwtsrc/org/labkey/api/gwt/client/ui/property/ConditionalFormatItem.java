/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
package org.labkey.api.gwt.client.ui.property;

import com.extjs.gxt.ui.client.event.BaseEvent;
import com.extjs.gxt.ui.client.event.ColorPaletteEvent;
import com.extjs.gxt.ui.client.event.Events;
import com.extjs.gxt.ui.client.event.Listener;
import com.extjs.gxt.ui.client.widget.ColorPalette;
import com.extjs.gxt.ui.client.widget.form.Field;
import com.extjs.gxt.ui.client.widget.form.FieldSet;
import com.extjs.gxt.ui.client.widget.form.TextField;
import com.extjs.gxt.ui.client.widget.form.Validator;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PushButton;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import org.labkey.api.gwt.client.model.GWTConditionalFormat;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.PropertyPane;
import org.labkey.api.gwt.client.ui.PropertyType;
import org.labkey.api.gwt.client.ui.Tooltip;
import org.labkey.api.gwt.client.ui.WindowUtil;
import org.labkey.api.gwt.client.util.FlexTableRowDragController;
import org.labkey.api.gwt.client.util.FlexTableRowDropController;
import org.labkey.api.gwt.client.util.PropertyUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * User: jeckels
 * Date: August 23, 2010
 */
public class ConditionalFormatItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType> //implements ValidatorDialog.UpdateListener
{
    private List<GWTConditionalFormat> _formats = new ArrayList<GWTConditionalFormat>();
    private FlexTable _formatTable;
    private HTML _noValidatorsLabel = new HTML("<i>This field has no conditional formats</i>");
    private ImageButton _addFormatButton;
    private FieldType _field;
    private GWTConditionalFormat _activeFormat;
    private FlexTableRowDragController _tableRowDragController;

    public ConditionalFormatItem(RootPanel rootPanel, PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
        DOM.setStyleAttribute(rootPanel.getElement(), "position", "relative");
        DOM.setStyleAttribute(rootPanel.getElement(), "overflow", "hidden");
        _tableRowDragController = new FlexTableRowDragController(rootPanel);
    }

    @Override
    public int addToTable(FlexTable flexTable, int row)
    {
        int col = 1;
        _formatTable = new FlexTable();
        _formatTable.getFlexCellFormatter().setWidth(0, col, "300px");
        _formatTable.setHTML(0, col++, "Conditional Formats");
        _formatTable.setHTML(0, col++, "<span style='font-weight: bold;'>B</span>");
        flexTable.getFlexCellFormatter().setAlignment(0, col - 1, HasHorizontalAlignment.ALIGN_CENTER, HasVerticalAlignment.ALIGN_MIDDLE);
        _formatTable.setHTML(0, col++, "<span style='font-style: italic;'>I</span>");
        flexTable.getFlexCellFormatter().setAlignment(0, col - 1, HasHorizontalAlignment.ALIGN_CENTER, HasVerticalAlignment.ALIGN_MIDDLE);
        _formatTable.setHTML(0, col++, "<span style='text-decoration: line-through;'>S</span>");
        flexTable.getFlexCellFormatter().setAlignment(0, col - 1, HasHorizontalAlignment.ALIGN_CENTER, HasVerticalAlignment.ALIGN_MIDDLE);

        flexTable.setWidget(row, LABEL_COLUMN, _formatTable);
        flexTable.getFlexCellFormatter().setColSpan(row, LABEL_COLUMN, 2);

        _formatTable.setVisible(false);

        flexTable.setWidget(++row, LABEL_COLUMN, _noValidatorsLabel);
        flexTable.getFlexCellFormatter().setColSpan(row, LABEL_COLUMN, 2);
        flexTable.getFlexCellFormatter().setHorizontalAlignment(row, 0, HasHorizontalAlignment.ALIGN_CENTER);

        FlexTableRowDropController controller = new FlexTableRowDropController(_formatTable)
        {
            @Override
            protected void handleDrop(FlexTable sourceTable, FlexTable targetTable, int sourceRow, int targetRow)
            {
                GWTConditionalFormat cf = _formats.remove(sourceRow - 1);
                _formats.add(targetRow - 1, cf);
                refreshFormats();
            }
        };
        _tableRowDragController.registerDropController(controller);

        _addFormatButton = new ImageButton("Add Conditional Format", new ClickHandler()
        {
            public void onClick(ClickEvent event)
            {
                showFilterDialog(new GWTConditionalFormat());
            }
        });
        flexTable.setWidget(++row, INPUT_COLUMN, _addFormatButton);
        
        return row;
    }

    public void showFilterDialog(GWTConditionalFormat format)
    {
        _activeFormat = format;
        PropertyType type = PropertyType.fromURI(getCurrentField().getRangeURI());
        if (type == null)
        {
            type = PropertyType.xsdString;
        }
        // Always treat the column as a VARCHAR to give us the most generally useful set of filters
        // Ideally we'd use the correct type for the display value we'll use when rendering, but we don't have that
        // easily available here. This means that we'll show options like "Starts With" for integers, and we'll
        // never offer a DATE_EQ type of comparison, but that's OK for this initial implementation.
        showFilterDialog(GWTConditionalFormat.DATA_REGION_NAME, GWTConditionalFormat.COLUMN_NAME, getCurrentField().getName(), "VARCHAR", getCurrentField().getMvEnabled(), _activeFormat.getFilter(), this);
    }

    public native static String getDescription(String queryString, String dataRegionName, String columnName)
    /*-{
        return $wnd.LABKEY.Filter.getFilterDescription(queryString, dataRegionName, columnName);
    }-*/;

    public native static void showFilterDialog(String dataRegionName, String colName, String caption, String dataType, boolean mvEnabled, String filter, ConditionalFormatItem handler)
    /*-{
        var callback = function(s1, s2, s3)
        {
            handler.@org.labkey.api.gwt.client.ui.property.ConditionalFormatItem::filterDialogCallback(Ljava/lang/Object;)(s3);
        };
        $wnd.showFilterPanel(dataRegionName, colName, caption, dataType, mvEnabled, filter, "Apply Conditional Format Where " + caption, callback);
    }-*/;

    /** Called from JSNI - do not delete without modifying the callback code immediately above */
    public void filterDialogCallback(Object filter)
    {
        _activeFormat.setFilter(filter.toString());
        propertyChanged(_activeFormat);
    }

    @Override
    public boolean copyValuesToPropertyDescriptor(FieldType field)
    {
        boolean changed = !PropertyUtil.nullSafeEquals(field.getConditionalFormats(), _formats);
        field.setConditionalFormats(cloneList(_formats));
        return changed;
    }

    /** Does a deep copy on the list */
    private List<GWTConditionalFormat> cloneList(List<GWTConditionalFormat> formats)
    {
        List<GWTConditionalFormat> result = new ArrayList<GWTConditionalFormat>();
        for (GWTConditionalFormat format : formats)
        {
            result.add(new GWTConditionalFormat(format));
        }
        return result;
    }

    public void enabledChanged()
    {
        _addFormatButton.setEnabled(isEnabled());
    }

    @Override
    public void showPropertyDescriptor(DomainType domain, FieldType field)
    {
        _field = field;
        _formats = cloneList(field.getConditionalFormats());
        refreshFormats();
    }

    public FieldType getCurrentField()
    {
        return _field;
    }

    private void refreshFormats()
    {
        int row = 1;
        int rowCount = _formatTable.getRowCount() - 1;

        while (rowCount > 0)
            _formatTable.removeRow(rowCount--);

        for (final GWTConditionalFormat cf : _formats)
        {
            HorizontalPanel panel = new HorizontalPanel();

            ClickHandler editHandler = new ClickHandler()
            {
                public void onClick(ClickEvent event)
                {
                    showFilterDialog(cf);
                }
            };

            if (_addFormatButton.isEnabled())
            {
                PushButton deleteButton = new PushButton(new Image(PropertyUtil.getContextPath() + "/_images/partdelete.gif"));
                deleteButton.addClickHandler(new ClickHandler()
                {
                    public void onClick(ClickEvent event)
                    {
                        _formats.remove(cf);
                        _propertyPane.copyValuesToPropertyDescriptor();
                        refreshFormats();
                    }
                });
                Tooltip.addTooltip(deleteButton, "Remove this conditional format");

                panel.add(deleteButton);
                panel.add(new HTML("&nbsp;"));
                PushButton editButton = new PushButton(new Image(PropertyUtil.getContextPath() + "/_images/partedit.gif"));
                Tooltip.addTooltip(editButton, "Edit condition for this format");
                editButton.addClickHandler(editHandler);

                panel.add(editButton);
            }

            _formatTable.setWidget(row, 0, panel);
            String description = getDescription(cf.getFilter(), GWTConditionalFormat.DATA_REGION_NAME, GWTConditionalFormat.COLUMN_NAME);
            Label descriptionWidget = new Label(description);
            if (_addFormatButton.isEnabled())
            {
                _tableRowDragController.makeDraggable(descriptionWidget);
                descriptionWidget.addClickHandler(editHandler);
            }
            _formatTable.setWidget(row, 1, descriptionWidget);

            final CheckBox boldCheckBox = new CheckBox();
            boldCheckBox.setValue(cf.isBold());
            boldCheckBox.setEnabled(_addFormatButton.isEnabled());
            boldCheckBox.addValueChangeHandler(new ValueChangeHandler<Boolean>()
            {
                public void onValueChange(ValueChangeEvent<Boolean> e)
                {
                    cf.setBold(e.getValue().booleanValue());
                    propertyChanged(cf);
                }
            });
            _formatTable.setWidget(row, 2, boldCheckBox);

            final CheckBox italicCheckBox = new CheckBox();
            italicCheckBox.setValue(cf.isItalic());
            italicCheckBox.setEnabled(_addFormatButton.isEnabled());
            italicCheckBox.addValueChangeHandler(new ValueChangeHandler<Boolean>()
            {
                public void onValueChange(ValueChangeEvent<Boolean> e)
                {
                    cf.setItalic(e.getValue().booleanValue());
                    propertyChanged(cf);
                }
            });
            _formatTable.setWidget(row, 3, italicCheckBox);

            final CheckBox strikethroughCheckBox = new CheckBox();
            strikethroughCheckBox.setValue(cf.isStrikethrough());
            strikethroughCheckBox.setEnabled(_addFormatButton.isEnabled());
            strikethroughCheckBox.addValueChangeHandler(new ValueChangeHandler<Boolean>()
            {
                public void onValueChange(ValueChangeEvent<Boolean> e)
                {
                    cf.setStrikethrough(e.getValue().booleanValue());
                    propertyChanged(cf);
                }
            });
            _formatTable.setWidget(row, 4, strikethroughCheckBox);

            final HTML colorsWidget = new HTML(getColorsHTML(cf));
            colorsWidget.setSize("15px", "15px");
            Tooltip.addTooltip(colorsWidget, "Click to select colors");

            if (_addFormatButton.isEnabled())
            {
                colorsWidget.addClickHandler(new ClickHandler()
                {
                    public void onClick(ClickEvent event)
                    {
                        final DialogBox dialog = new DialogBox(false, true);

                        final TextField<String> foregroundTextField = new TextField<String>();
                        final TextField<String> backgroundTextField = new TextField<String>();

                        final ColorPalette foregroundPalette = new ColorPalette();
                        final ColorPalette backgroundPalette = new ColorPalette();

                        final ImageButton okButton = new ImageButton("OK", new ClickHandler()
                        {
                            public void onClick(ClickEvent event)
                            {
                                cf.setBackgroundColor(backgroundTextField.getValue());
                                cf.setTextColor(foregroundTextField.getValue());
                                colorsWidget.setHTML(getColorsHTML(cf));
                                dialog.hide();
                                propertyChanged(cf);
                            }
                        });
                        ImageButton cancelButton = new ImageButton("Cancel", new ClickHandler()
                        {
                            public void onClick(ClickEvent event)
                            {
                                dialog.hide();
                            }
                        });
                        
                        configureColorPicker(foregroundTextField, foregroundPalette, cf.getTextColor());
                        configureColorPicker(backgroundTextField, backgroundPalette, cf.getBackgroundColor());

                        Listener<BaseEvent> okEnablerListener = new Listener<BaseEvent>()
                        {
                            public void handleEvent(BaseEvent baseEvent)
                            {
                                okButton.setEnabled(_colorValidator.validate(foregroundTextField, foregroundTextField.getValue()) == null &&
                                        _colorValidator.validate(backgroundTextField, backgroundTextField.getValue()) == null);
                            }
                        };
                        foregroundTextField.addListener(Events.Change, okEnablerListener);
                        backgroundTextField.addListener(Events.Change, okEnablerListener);

                        // Fire the listener once to initialize the dialog correctly
                        okEnablerListener.handleEvent(null);

                        FieldSet foregroundFieldSet = new FieldSet();
                        foregroundFieldSet.setHeading("Foreground Color");
                        foregroundFieldSet.add(foregroundTextField);
                        foregroundFieldSet.add(foregroundPalette);

                        FieldSet backgroundFieldSet = new FieldSet();
                        backgroundFieldSet.setHeading("Background Color");
                        backgroundFieldSet.add(backgroundTextField);
                        backgroundFieldSet.add(backgroundPalette);

                        HorizontalPanel buttonPanel = new HorizontalPanel();
                        buttonPanel.add(okButton);
                        buttonPanel.add(cancelButton);

                        VerticalPanel mainPanel = new VerticalPanel();
                        mainPanel.add(backgroundFieldSet);
                        mainPanel.add(foregroundFieldSet);
                        mainPanel.add(new Label("Colors must be specified in"));
                        mainPanel.add(new Label("six character RGB hex format."));
                        mainPanel.add(new Label("For example, 'FF0000' is red."));
                        mainPanel.add(buttonPanel);
                        mainPanel.setCellHorizontalAlignment(buttonPanel, HasHorizontalAlignment.ALIGN_CENTER);

                        dialog.setWidget(mainPanel);
                        dialog.show();
                        WindowUtil.centerDialog(dialog);

                        selectColor(foregroundPalette, foregroundTextField.getValue());
                        selectColor(backgroundPalette, backgroundTextField.getValue());
                    }
                });
            }
            _formatTable.setWidget(row, 5, colorsWidget);

            row++;
        }

        _formatTable.setVisible(!_formats.isEmpty());
        _noValidatorsLabel.setVisible(_formats.isEmpty());
    }

    private Validator _colorValidator = new Validator()
    {
        public String validate(Field field, String s)
        {
            if (s == null || s.length() == 0)
            {
                return null;
            }
            if (!s.matches(GWTConditionalFormat.COLOR_REGEX))
            {
                return "Color must be specified as a 6 digit hex value";
            }
            return null;
        }
    };

    private String getColorsHTML(GWTConditionalFormat cf)
    {
        String background = cf.getBackgroundColor();
        if (background == null || background.isEmpty())
        {
            background = "#ffffff";
        }
        if (!background.startsWith("#"))
        {
            background = "#" + background;
        }
        String foreground = cf.getTextColor();
        if (foreground == null || foreground.isEmpty())
        {
            foreground = "#000000";
        }
        if (!foreground.startsWith("#"))
        {
            foreground = "#" + foreground;
        }
        return "<span style=\"position: absolute; border: 1px black solid; width: 8px; z-index: 12; height: 8px; background-color: " + foreground + "\"></span>\n" +
               "<span style=\"position: absolute; border: 1px black solid; width: 8px; margin: 4px; z-index: 10; height: 8px; background-color: " + background + "\"></span>";
    }

    private void configureColorPicker(final TextField<String> textBox, final ColorPalette palette, String initialColor)
    {
        textBox.setValue(initialColor);
        textBox.setValidator(_colorValidator);

        palette.addListener(Events.Select, new Listener<ColorPaletteEvent>()
        {
            public void handleEvent(ColorPaletteEvent e)
            {
                if (textBox.getValue() == null || !textBox.getValue().equals(e.getColor()))
                {
                    textBox.setValue(e.getColor());
                }
            }
        });

        textBox.addListener(Events.Change, new Listener<BaseEvent>()
        {
            public void handleEvent(BaseEvent e)
            {
                if (textBox.getValue() != null && !textBox.getValue().equals(palette.getValue()) &&
                    _colorValidator.validate(textBox, textBox.getValue()) == null)
                {
                    selectColor(palette, textBox.getValue());
                }
            }
        });
    }

    private void selectColor(ColorPalette palette, String desiredColor)
    {
        if (desiredColor != null && !desiredColor.isEmpty())
        {
            boolean foundColor = false;
            for (String color : palette.getColors())
            {
                if (color.equals(desiredColor))
                {
                    foundColor = true;
                    break;
                }
            }
            if (!foundColor)
            {
                String[] colors = palette.getColors();
                colors[colors.length - 1] = desiredColor;
                palette.setColors(colors);
            }
            palette.select(desiredColor);
        }
    }

    public void propertyChanged(GWTConditionalFormat format)
    {
        if (!_formats.contains(format))
        {
            _formats.add(format);
        }
        refreshFormats();
        _propertyPane.copyValuesToPropertyDescriptor();
    }

    public void propertyDescriptorChanged(FieldType field)
    {
        showPropertyDescriptor(null, field);
    }
}