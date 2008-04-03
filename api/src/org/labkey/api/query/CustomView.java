package org.labkey.api.query;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

public interface CustomView
{
    enum ColumnProperty
    {
        columnTitle
    }

    QueryDefinition getQueryDefinition();
    String getName();
    User getOwner();
    Container getContainer();
    boolean canInherit();
    void setCanInherit(boolean f);
    boolean isHidden();
    void setIsHidden(boolean f);


    List<FieldKey> getColumns();
    List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> getColumnProperties();
    void setColumns(List<FieldKey> columns);
    void setColumnProperties(List<Map.Entry<FieldKey, Map<ColumnProperty,String>>> list);

    void applyFilterAndSortToURL(ActionURL url, String dataRegionName);
    void setFilterAndSortFromURL(ActionURL url, String dataRegionName);
    boolean hasFilterOrSort();

    void save(User user, HttpServletRequest request) throws QueryException;
    void delete(User user, HttpServletRequest request) throws QueryException;
}
