package org.labkey.api.gwt.client.ui.property;

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.ListBox;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.ui.PropertyPane;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Apr 11, 2012
 */
public class FacetingBehaviorItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType>
{
    private ListBox _facetingBehaviorTypes = new ListBox();
    private HelpPopup _helpPopup = new HelpPopup("Faceting Behavior", "");

    public FacetingBehaviorItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
    }

    private void updateEnabledState(FieldType field)
    {
        setCanEnable(true);

        if (_facetingBehaviorTypes.getItemCount() == 0)
        {
            for (FacetingBehaviorType type : FacetingBehaviorType.values())
                _facetingBehaviorTypes.addItem(type.getLabel(), type.name());
        }

        String filterBehaviorType = field.getFacetingBehaviorType();

        for (int i = 0; i < _facetingBehaviorTypes.getItemCount(); i++)
        {
            String currentItemValue = _facetingBehaviorTypes.getValue(i);
            if (currentItemValue.equals(filterBehaviorType))
            {
                _facetingBehaviorTypes.setSelectedIndex(i);
                return;
            }
        }
        _facetingBehaviorTypes.setSelectedIndex(0);
    }

    public int addToTable(FlexTable flexTable, int row)
    {
        FlowPanel labelPanel = new FlowPanel();
        labelPanel.add(new InlineHTML("Faceted Filter Behavior"));
        labelPanel.add(_helpPopup);
        flexTable.setWidget(row, LABEL_COLUMN, labelPanel);

        FlexTable filterTypeTable = new FlexTable();
        filterTypeTable.setWidget(0, 0, _facetingBehaviorTypes);
        flexTable.setWidget(row, INPUT_COLUMN, filterTypeTable);

        _facetingBehaviorTypes.addClickHandler(createClickHandler());
        _facetingBehaviorTypes.addKeyUpHandler(createKeyUpHandler());

        return row++;
    }

    public boolean copyValuesToPropertyDescriptor(FieldType field)
    {
        String type = _facetingBehaviorTypes.getValue(_facetingBehaviorTypes.getSelectedIndex());

        boolean changed = !type.equals(field.getFacetingBehaviorType());
        if (changed)
            field.setFacetingBehaviorType(type);
        return changed;
    }


    public void enabledChanged()
    {
        boolean enabled = isEnabled();
        _facetingBehaviorTypes.setEnabled(enabled);
    }

    public void showPropertyDescriptor(DomainType domain, FieldType field)
    {
        _helpPopup.setBody(FacetingBehaviorType.getHelpPopupHtml());

        updateEnabledState(field);
    }

    @Override
    public void propertyDescriptorChanged(FieldType field)
    {
        updateEnabledState(field);
    }
}
