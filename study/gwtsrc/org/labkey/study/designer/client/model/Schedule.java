package org.labkey.study.designer.client.model;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Dec 20, 2006
 * Time: 4:26:30 PM
 * To change this template use File | Settings | File Templates.
 */
public interface Schedule /*extends XMLSavable*/
{
    void removeTimepoint(GWTTimepoint tp);

    List/*<Timepoint>*/ getTimepoints();

    GWTTimepoint getTimepoint(int i);

    void addTimepoint(GWTTimepoint tp);

    void addTimepoint(int index, GWTTimepoint tp);

    void removeTimepoint(int index);

}
