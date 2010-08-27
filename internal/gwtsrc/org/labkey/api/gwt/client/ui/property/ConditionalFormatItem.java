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

import com.extjs.gxt.ui.client.event.ColorPaletteEvent;
import com.extjs.gxt.ui.client.event.Events;
import com.extjs.gxt.ui.client.event.Listener;
import com.extjs.gxt.ui.client.widget.ColorPalette;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.DockPanel;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PushButton;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
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
        showFilterDialog(GWTConditionalFormat.DATA_REGION_NAME, GWTConditionalFormat.COLUMN_NAME, getCurrentField().getName(), type.getSqlName(), getCurrentField().getMvEnabled(), _activeFormat.getFilter(), this);
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
        field.setConditionalFormats(_formats);
        return changed;
    }

    public void enabledChanged()
    {
        _addFormatButton.setEnabled(isEnabled());
    }

    @Override
    public void showPropertyDescriptor(DomainType domain, FieldType field)
    {
        _field = field;
        _addFormatButton.setVisible(true);
        _formats = field.getConditionalFormats();
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

            colorsWidget.addClickHandler(new ClickHandler()
            {
                public void onClick(ClickEvent event)
                {
                    final DialogBox dialog = new DialogBox(false, true);
                    dialog.setTitle("Conditional Format Colors");

                    final TextBox foregroundTextField = new TextBox();
                    foregroundTextField.setText(cf.getTextColor());
                    final TextBox backgroundTextField = new TextBox();
                    backgroundTextField.setText(cf.getBackgroundColor());

                    final ColorPalette foregroundPalette = new ColorPalette();
                    configureColorPicker(foregroundTextField, foregroundPalette);

                    final ColorPalette backgroundPalette = new ColorPalette();
                    configureColorPicker(backgroundTextField, backgroundPalette);

                    FlexTable table = new FlexTable();
                    table.setText(0, 0, "Foreground:");
                    table.setWidget(0, 1, foregroundTextField);
                    table.setWidget(0, 2, foregroundPalette);

                    table.setText(1, 0, "Background:");
                    table.setWidget(1, 1, backgroundTextField);
                    table.setWidget(1, 2, backgroundPalette);

                    ImageButton okButton = new ImageButton("OK", new ClickHandler()
                    {
                        public void onClick(ClickEvent event)
                        {
                            cf.setBackgroundColor(backgroundTextField.getText());
                            cf.setTextColor(foregroundTextField.getText());
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

                    HorizontalPanel buttonPanel = new HorizontalPanel();
                    buttonPanel.add(okButton);
                    buttonPanel.add(cancelButton);

                    DockPanel mainPanel = new DockPanel();
                    mainPanel.add(table, DockPanel.CENTER);
                    mainPanel.add(buttonPanel, DockPanel.SOUTH);

                    dialog.setWidget(mainPanel);
                    WindowUtil.centerDialog(dialog);
                    dialog.show();

                    selectColor(foregroundPalette, foregroundTextField.getText());
                    selectColor(backgroundPalette, backgroundTextField.getText());
                }
            });
            _formatTable.setWidget(row, 5, colorsWidget);

            row++;
        }

        _formatTable.setVisible(!_formats.isEmpty());
        _noValidatorsLabel.setVisible(_formats.isEmpty());
    }

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

    private void configureColorPicker(final TextBox textBox, final ColorPalette palette)
    {
        palette.addListener(Events.Select, new Listener<ColorPaletteEvent>()
        {
            public void handleEvent(ColorPaletteEvent e)
            {
                if (!textBox.getText().equals(e.getColor()))
                {
                    textBox.setText(e.getColor());
                }
            }
        });

        textBox.addChangeHandler(new ChangeHandler()
        {
            public void onChange(ChangeEvent event)
            {
                if (!textBox.getText().equals(palette.getValue()))
                {
                    palette.setValue(textBox.getText());
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