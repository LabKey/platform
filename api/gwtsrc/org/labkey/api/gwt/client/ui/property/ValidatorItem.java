/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTPropertyValidator;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.gwt.client.ui.*;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public class ValidatorItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType> implements ValidatorDialog.UpdateListener
{
    private List<GWTPropertyValidator> _validators = new ArrayList<GWTPropertyValidator>();
    private FlexTable _validatorTable;
    private HTML _noValidatorsLabel = new HTML("<i>This field has no validators</i>");
    private ImageButton _addRangeButton;
    private ImageButton _addRegexButton;
    private ImageButton _addLookupButton;
    private boolean _validatorChanged;
    private HorizontalPanel _regexPanel;
    private HorizontalPanel _rangePanel;
    private HorizontalPanel _lookupPanel;

    public ValidatorItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
    }

    @Override
    public int addToTable(FlexTable flexTable, int row)
    {
        _addRegexButton = new ImageButton("Add RegEx Validator", new RegexClickHandler());
        _regexPanel = new HorizontalPanel();
        _regexPanel.add(_addRegexButton);
        _regexPanel.add(PropertyValidatorType.RegEx.createHelpPopup());
        flexTable.setWidget(row++, INPUT_COLUMN, _regexPanel);
        _addRangeButton = new ImageButton("Add Range Validator", new RangeClickHandler());
        _rangePanel = new HorizontalPanel();
        _rangePanel.add(_addRangeButton);
        _rangePanel.add(PropertyValidatorType.Range.createHelpPopup());
        flexTable.setWidget(row++, INPUT_COLUMN, _rangePanel);
        _addLookupButton = new ImageButton("Add Lookup Validator", new LookupClickHandler());
        _lookupPanel = new HorizontalPanel();
        _lookupPanel.add(_addLookupButton);
        _lookupPanel.add(PropertyValidatorType.Lookup.createHelpPopup());
        flexTable.setWidget(row++, INPUT_COLUMN, _lookupPanel);

        _validatorTable = new FlexTable();
        int col = 1;

        _validatorTable.getFlexCellFormatter().setWidth(0, col, "150px");
        _validatorTable.setHTML(0, col++, "<i>Name</i>");
        _validatorTable.getFlexCellFormatter().setWidth(0, col, "150px");
        _validatorTable.setHTML(0, col++, "<i>Description</i>");
        _validatorTable.getFlexCellFormatter().setWidth(0, col, "85px");
        _validatorTable.setHTML(0, col++, "<i>Type</i>");

        flexTable.setWidget(row, LABEL_COLUMN, _validatorTable);
        flexTable.getFlexCellFormatter().setColSpan(row, 0, 2);

        _validatorTable.setVisible(false);

        flexTable.setWidget(++row, LABEL_COLUMN, _noValidatorsLabel);
        flexTable.getFlexCellFormatter().setColSpan(row, 0, 2);
        flexTable.getFlexCellFormatter().setHorizontalAlignment(row, 0, HasHorizontalAlignment.ALIGN_CENTER);

        return row;
    }

    @Override
    public boolean copyValuesToPropertyDescriptor(FieldType field)
    {
        boolean result = _validatorChanged;
        field.setPropertyValidators(_validators);
        _validatorChanged = false;
        return result;
    }

    public void enabledChanged()
    {
        _addRegexButton.setEnabled(isEnabled());
        _addRangeButton.setEnabled(isEnabled());
        _addLookupButton.setEnabled(isEnabled());

        // 24683: Capability to edit validators not always showing up in edit mode
        refreshValidators();
    }

    @Override
    public void showPropertyDescriptor(DomainType domain, FieldType field)
    {
        String rangeURI = field.getRangeURI();
        PropertyType t = PropertyType.fromName(rangeURI);
        _rangePanel.setVisible(PropertyType.xsdDateTime == t || PropertyType.xsdDouble == t || PropertyType.xsdInt == t);
        _validators = field.getPropertyValidators();
        boolean hasLookup = field.getLookupQuery() != null && field.getLookupSchema() != null;
        boolean hasLookupValidator = false;
        for (GWTPropertyValidator validator : _validators)
        {
            if (validator.getType() == PropertyValidatorType.Lookup)
            {
                hasLookupValidator = true;
                break;
            }
        }
        _lookupPanel.setVisible(hasLookup && !hasLookupValidator);
        refreshValidators();
    }

    class RegexClickHandler implements ClickHandler
    {
        GWTPropertyValidator _validator;

        RegexClickHandler()
        {
            this(null);
        }

        RegexClickHandler(GWTPropertyValidator validator)
        {
            _validator = validator;
        }

        public void onClick(ClickEvent event)
        {
            RegexValidatorDialog dlg = new RegexValidatorDialog(_validator == null ? new GWTPropertyValidator() : _validator);

            dlg.setListener(ValidatorItem.this);
            dlg.center();
            dlg.show();
        }
    }

    class RangeClickHandler implements ClickHandler
    {
        GWTPropertyValidator _validator;

        RangeClickHandler()
        {
            this(null);
        }

        RangeClickHandler(GWTPropertyValidator validator)
        {
            _validator = validator;
        }

        public void onClick(ClickEvent event)
        {
            RangeValidatorDialog dlg = new RangeValidatorDialog(_validator == null ? new GWTPropertyValidator() : _validator);

            dlg.setListener(ValidatorItem.this);
            dlg.center();
            dlg.show();
        }
    }

    class LookupClickHandler implements ClickHandler
    {
        GWTPropertyValidator _validator;

        LookupClickHandler()
        {
            this(null);
        }

        LookupClickHandler(GWTPropertyValidator validator)
        {
            _validator = validator;
        }

        public void onClick(ClickEvent event)
        {
            // No need for a dialog on this one - there are no options
            GWTPropertyValidator validator = new GWTPropertyValidator();
            validator.setName("Lookup validator");
            validator.setType(PropertyValidatorType.Lookup);
            propertyChanged(validator);
        }
    }

    private void refreshValidators()
    {
        int row = 1;
        int rowCount = _validatorTable.getRowCount() - 1;

        while (rowCount > 0)
            _validatorTable.removeRow(rowCount--);

        int visibleValidators = 0;
        for (final GWTPropertyValidator pv : _validators)
        {
            if (!pv.getType().isHidden())
            {
                visibleValidators += 1;
                HorizontalPanel panel = new HorizontalPanel();

                if (_addRangeButton.isEnabled())
                {
                    PushButton deleteButton = new PushButton(new Image(PropertyUtil.getContextPath() + "/_images/partdelete.gif"));
                    deleteButton.addClickHandler(new ClickHandler()
                    {
                        public void onClick(ClickEvent event)
                        {
                            _validators.remove(pv);
                            _validatorChanged = true;
                            _propertyPane.copyValuesToPropertyDescriptor();
                            refreshValidators();
                        }
                    });
                    Tooltip.addTooltip(deleteButton, "Remove this validator");

                    panel.add(deleteButton);
                    panel.add(new HTML("&nbsp;"));
                    PushButton editButton = new PushButton(new Image(PropertyUtil.getContextPath() + "/_images/partedit.gif"));
                    Tooltip.addTooltip(editButton, "Edit this validator");
                    if (pv.getType().equals(PropertyValidatorType.Range))
                    {
                        editButton.addClickHandler(new RangeClickHandler(pv));
                    }
                    else if (pv.getType().equals(PropertyValidatorType.RegEx))
                    {
                        editButton.addClickHandler(new RegexClickHandler(pv));
                    }
                    if (pv.getType().isConfigurable())
                    {
                        panel.add(editButton);
                    }
                }

                _validatorTable.setWidget(row, 0, panel);
                _validatorTable.setWidget(row, 1, new HTML(StringUtils.filter(pv.getName(), true)));
                _validatorTable.setWidget(row, 2, new HTML(StringUtils.filter(pv.getDescription(), true)));
                _validatorTable.setWidget(row, 3, new Label(pv.getType().name()));
                _validatorTable.setWidget(row++, 4, pv.getType().createHelpPopup());
            }
        }

        _validatorTable.setVisible(visibleValidators > 0);
        _noValidatorsLabel.setVisible(visibleValidators <= 0);
    }

    public void propertyChanged(GWTPropertyValidator validator)
    {
        if (validator.isNew() && validator.getRowId() == 0)
        {
            validator.setNew(false);
            _validators.add(validator);
        }
        _validatorChanged = true;
        refreshValidators();
        _propertyPane.copyValuesToPropertyDescriptor();
    }

    public void propertyDescriptorChanged(FieldType field)
    {
        showPropertyDescriptor(null, field);
    }
}