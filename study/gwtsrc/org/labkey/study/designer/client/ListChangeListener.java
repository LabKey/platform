package org.labkey.study.designer.client;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Dec 22, 2006
 * Time: 10:05:42 AM
 * To change this template use File | Settings | File Templates.
 */
public interface ListChangeListener
{
    void itemInserted(List list, int position, Object itemInserted, Object sender);
    void itemDeleted(List list, int position, Object itemDeleted, Object sender);
}
