package org.labkey.api.gwt.client.ui.property;

import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.ui.PropertyPane;
import org.labkey.api.gwt.client.ui.PropertyType;

/**
 * User: cnathe
 * Date: March 18, 2014
 */
public class DefaultScaleItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType>
{
    protected Label _label = null;
    private ListBox _scaleTypes = new ListBox();
    private HelpPopup _helpPopup = new HelpPopup("Default Scale Type", "For numeric field types, defines whether linear"
                                                + " or log scales will be used by default for this measure.");

    public DefaultScaleItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
    }

    private void updateEnabledState(FieldType field)
    {
        PropertyType type = PropertyType.fromURI(field.getRangeURI());
        boolean isValidNumeric = (type == PropertyType.xsdDouble || type == PropertyType.xsdInt);
        setCanEnable(isValidNumeric);

        if (_scaleTypes.getItemCount() == 0)
        {
            for (DefaultScaleType t : DefaultScaleType.values())
                _scaleTypes.addItem(t.getLabel(), t.name());
        }

        String scaleType = field.getDefaultScale();

        for (int i = 0; i < _scaleTypes.getItemCount(); i++)
        {
            String currentItemValue = _scaleTypes.getValue(i);
            if (currentItemValue.equals(scaleType))
            {
                _scaleTypes.setSelectedIndex(i);
                return;
            }
        }
        _scaleTypes.setSelectedIndex(0);
    }

    public int addToTable(FlexTable flexTable, int row)
    {
        FlowPanel labelPanel = new FlowPanel();
        _label = new InlineHTML("Default Scale Type");
        labelPanel.add(_label);
        labelPanel.add(_helpPopup);
        flexTable.setWidget(row, LABEL_COLUMN, labelPanel);

        FlexTable typeTable = new FlexTable();
        typeTable.setWidget(0, 0, _scaleTypes);
        flexTable.setWidget(row, INPUT_COLUMN, typeTable);

        _scaleTypes.addClickHandler(createClickHandler());
        _scaleTypes.addKeyUpHandler(createKeyUpHandler());

        return row++;
    }

    public boolean copyValuesToPropertyDescriptor(FieldType field)
    {
        String type = _scaleTypes.getValue(_scaleTypes.getSelectedIndex());

        boolean changed = !type.equals(field.getDefaultScale());
        field.setDefaultScale(type);
        return changed;
    }


    public void enabledChanged()
    {
        boolean enabled = isEnabled();

        if (enabled)
            removeClass(_label, "labkey-disabled");
        else
            addClass(_label, "labkey-disabled");

        _scaleTypes.setEnabled(enabled);
        _helpPopup.setVisible(enabled);
    }

    public void showPropertyDescriptor(DomainType domain, FieldType field)
    {
        updateEnabledState(field);
    }

    @Override
    public void propertyDescriptorChanged(FieldType field)
    {
        updateEnabledState(field);
    }
}