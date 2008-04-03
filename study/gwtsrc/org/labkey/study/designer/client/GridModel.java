package org.labkey.study.designer.client;

import com.google.gwt.user.client.ui.Widget;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Dec 14, 2006
 * Time: 2:39:57 PM
 * To change this template use File | Settings | File Templates.
 */
public interface GridModel
{
    int getDataColumnCount();
    int getDataRowCount();
    Widget getCellWidget(int row, int col);
    int getHeaderRows();
    Widget getColumnHeader(int row, int column);
    void setOwner(EditableGrid grid);
    void makeGhostRowReal();
}
