package org.labkey.api.qc;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.List;

public interface QCStateHandler<FORM extends AbstractManageQCStatesForm>
{
    List<QCState> getQCStates(Container container);
    boolean isQCStateInUse(Container container, QCState state);
    boolean isBlankQCStatePublic(Container container);
    void updateQcState(Container container, FORM form, User user);
    static <T> boolean nullSafeEqual(T first, T second)
    {
        if (first == null && second == null)
            return true;
        if (first == null)
            return false;
        return first.equals(second);
    }
}
