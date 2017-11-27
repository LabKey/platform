/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.ui.LabKeyLinkHTML;
import org.labkey.api.gwt.client.ui.PropertyPane;
import org.labkey.api.gwt.client.ui.Saveable;
import org.labkey.api.gwt.client.ui.WindowUtil;
import org.labkey.api.gwt.client.util.StringUtils;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public class DefaultValueItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType>
{
    private ListBox _defaultValueTypes = new ListBox();
    private Label _currentDefault = new Label();
    private Saveable<GWTDomain> _owner;
    private HelpPopup _helpPopup = new HelpPopup("Default Value Types", "");
    private GWTDomain _domain;
    private LabKeyLinkHTML _setDefaultValueLink;
    private static final String SETVALUE_LINK_NOTSUPPORTED = "<span class='labkey-disabled'><i>Not supported for file/attachment fields.</i></span>";
    private FlexTable _defaultTypeTable;
    private FlexTable _defaultValueTable;
    private InlineHTML _defaultTypeLabel;
    private InlineHTML _defaultValueLabel;

    private boolean _domainSupportsDefaultValues = true;
    
    public DefaultValueItem(Saveable<GWTDomain> owner, PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
        _owner = owner;
        _defaultValueTypes.setName("defaultValue");
    }

    private boolean supportsDefaultValues(GWTDomain d)
    {
        return (d.getDefaultValueOptions() != null &&
                d.getDefaultValueOptions().length > 0);
    }


    private void updateEnabledState(FieldType field)
    {
        if (!_domainSupportsDefaultValues)
            return;
        setCanEnable(true);

        if (_defaultValueTypes.getItemCount() == 0)
        {
            for (DefaultValueType type : _domain.getDefaultValueOptions())
                _defaultValueTypes.addItem(type.getLabel(), type.name());
        }
        if (field.isFileType())
        {
            setCanEnable(false);
            setEnabled(false);
        }

        DefaultValueType defaultType = field.getDefaultValueType();
        if (defaultType == null)
            defaultType = _domain.getDefaultDefaultValueType();
        if (field.isFileType())
            defaultType = DefaultValueType.FIXED_EDITABLE;
        for (int i = 0; i < _defaultValueTypes.getItemCount(); i++)
        {
            String currentItemValue = _defaultValueTypes.getValue(i);
            if (currentItemValue.equals(defaultType.name()))
            {
                _defaultValueTypes.setSelectedIndex(i);
                return;
            }
        }
        _defaultValueTypes.setSelectedIndex(0);
    }

    private class SetDefaultValue_ClickHandler implements ClickHandler
    {
        public void onClick(ClickEvent e)
        {
            if (!isEnabled())
                return;

            if (_owner.isDirty())
            {
                // Unsaved changes. Prompt user
                if (Window.confirm("You must save your changes before setting default values.  Save changes?"))
                {
                    // Only save and redirect if they say yes.
                    _owner.save(new Saveable.SaveListener<GWTDomain>()
                    {
                        public void saveSuccessful(GWTDomain domain, String designerUrl)
                        {
                            String actionURL = domain.getDefaultValuesURL();
                            //issue 14006: changed encodeComponent to encodePathSegment, b/c the former will convert spaces to '+'
                            String queryString = "returnUrl=" + URL.encodePathSegment(designerUrl) + "&domainId=" + domain.getDomainId();
                            boolean hasQueryString = actionURL.indexOf('?') > 0;
                            final String url = actionURL + (hasQueryString ? "&" : "?") + queryString;
                            WindowUtil.setLocation(url);
                        }
                    });
                }
            }
            else
            {
                String actionURL = _domain.getDefaultValuesURL();
                String currentURL = _owner.getCurrentURL();
                //issue 14006: changed encodeComponent to encodePathSegment, b/c the former will convert spaces to '+'
                String queryString = "returnUrl=" + URL.encodePathSegment(currentURL) + "&domainId=" +  _propertyPane.getDomainId();
                boolean hasQueryString = actionURL.indexOf('?') > 0;
                final String url = actionURL + (hasQueryString ? "&" : "?") + queryString;
                WindowUtil.setLocation(url);
            }
        }
    }

    public int addToTable(FlexTable flexTable, int row)
    {
        FlowPanel labelPanel = new FlowPanel();
        _defaultTypeLabel = new InlineHTML("Default&nbsp;Type");
        labelPanel.add(_defaultTypeLabel);
        labelPanel.add(_helpPopup);
        flexTable.setWidget(row, LABEL_COLUMN, labelPanel);

        _setDefaultValueLink = new LabKeyLinkHTML("set&nbsp;value");
        _setDefaultValueLink.addClickHandler(new SetDefaultValue_ClickHandler());

        _defaultTypeTable = new FlexTable();
        _defaultTypeTable.setWidget(0, 0, _defaultValueTypes);
        flexTable.setWidget(row, INPUT_COLUMN, _defaultTypeTable);

        _defaultValueTypes.addClickHandler(createClickHandler());
        _defaultValueTypes.addKeyUpHandler(createKeyUpHandler());
        _defaultTypeTable.setWidget(0, 1, _setDefaultValueLink);

        _defaultValueTable = new FlexTable();
        _defaultValueTable.setWidget(0, 0, _currentDefault);
        _defaultValueTable.getFlexCellFormatter().getElement(0, 0).getStyle().setPaddingRight(1.5, Style.Unit.EM);
        _defaultValueTable.setWidget(0, 1, _setDefaultValueLink);

        _defaultValueLabel = new InlineHTML("Default&nbsp;Value");
        flexTable.setWidget(row + 1, LABEL_COLUMN, _defaultValueLabel);
        flexTable.setWidget(row + 1, INPUT_COLUMN, _defaultValueTable);

        return row + 2;
    }

    public boolean copyValuesToPropertyDescriptor(FieldType field)
    {
        String name = _defaultValueTypes.getValue(_defaultValueTypes.getSelectedIndex());
        DefaultValueType newType = DefaultValueType.valueOf(name);

        // we're "changed" if the old default value type doesn't equal the new type, UNLESS the old type was null
        // (indicating a legacy list) and the new value equals the domain default:
        boolean changed = (newType != field.getDefaultValueType()) &&
            !(field.getDefaultValueType() == null && _domain.getDefaultDefaultValueType() == newType);

        if (changed)
            field.setDefaultValueType(newType);

        return changed;
    }


    public void enabledChanged()
    {
        boolean enabled = isEnabled();
        _defaultValueTypes.setEnabled(enabled);
        _setDefaultValueLink.setVisible(enabled);

        if (getCanEnable())
        {
            _setDefaultValueLink.setLabKeyLinkHTML("set&nbsp;value");
            removeClass(_defaultValueLabel, "labkey-disabled");
            removeClass(_defaultTypeLabel, "labkey-disabled");
        }
        else
        {
            _setDefaultValueLink.setHTML(SETVALUE_LINK_NOTSUPPORTED);
            _setDefaultValueLink.setVisible(true);
            addClass(_defaultValueLabel, "labkey-disabled");
            addClass(_defaultTypeLabel, "labkey-disabled");
        }
    }


    public void showPropertyDescriptor(DomainType domain, FieldType field)
    {
        _domain = domain;
        _domainSupportsDefaultValues = supportsDefaultValues(domain);
    
        StringBuilder helpString = new StringBuilder();
        if (_domainSupportsDefaultValues)
        {
            DefaultValueType[] defaultTypes = _domain.getDefaultValueOptions();
            for (int i = 0; i < defaultTypes.length; i++)
            {
                DefaultValueType type = defaultTypes[i];
                helpString.append("<b>").append(type.getLabel()).append("</b>: ").append(type.getHelpText());
                if (i < defaultTypes.length - 1)
                    helpString.append("<br><br>");
            }
            _currentDefault.setText(field.getDefaultDisplayValue());
            _helpPopup.setBody(helpString.toString());
            updateEnabledState(field);
        }
        else
        {
            setCanEnable(false);
            String msg = "<span class='labkey-disabled'><i>Not supported for " +
                    StringUtils.filter(domain.getName()) + "</i></span>";
            _defaultTypeTable.clear();
            _defaultTypeTable.setWidget(0, 0, new HTML(msg));
            _defaultValueTable.clear();
            _defaultValueTable.setWidget(0, 0, new HTML("<span class='labkey-disabled'><i>None</i></span>"));
            _helpPopup.setBody(msg);
        }
    }

    @Override
    public void propertyDescriptorChanged(FieldType field)
    {
        updateEnabledState(field);
    }
}
