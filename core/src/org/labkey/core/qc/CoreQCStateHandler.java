package org.labkey.core.qc;

import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.qc.QCState;
import org.labkey.api.qc.QCStateHandler;
import org.labkey.api.qc.QCStateManager;
import org.labkey.api.security.User;
import org.labkey.core.CoreController;

import java.util.List;
import java.util.Map;

public class CoreQCStateHandler implements QCStateHandler<CoreController.ManageQCStatesForm>
{
    protected List<QCState> _states = null;
    public static final String PROPS_KEY = "CoreQCStateHandlerProps";
    public static final String IS_BLANK_QC_STATE_PUBLIC_KEY = "IsBlankQCStatePublic";
    public static final String DEFAULT_QC_STATE_KEY = "DefaultQCState";

    @Override
    public boolean isBlankQCStatePublic(Container container)
    {
        Map<String, String> props = PropertyManager.getNormalStore().getProperties(container, PROPS_KEY);
        String isBlankQCStatePublicString = props.get(IS_BLANK_QC_STATE_PUBLIC_KEY);
        if (isBlankQCStatePublicString == null)
            return false;  // TODO: should a different default be used?
        return Boolean.parseBoolean(props.get(IS_BLANK_QC_STATE_PUBLIC_KEY));
    }

    public Integer getDefaultQCState(Container container)
    {
        Map<String, String> props = PropertyManager.getNormalStore().getProperties(container, PROPS_KEY);
        String defaultQCStateKey = props.get(DEFAULT_QC_STATE_KEY);
        if (defaultQCStateKey == null)
            return null;
        return Integer.parseInt(props.get(DEFAULT_QC_STATE_KEY));
    }

    public void setProps(Container container, boolean isBlankQCStatePublic, Integer defaultQCState)
    {
        PropertyManager.PropertyMap props = PropertyManager.getNormalStore().getWritableProperties(container, PROPS_KEY, true);
        props.put(IS_BLANK_QC_STATE_PUBLIC_KEY, Boolean.toString(isBlankQCStatePublic));
        if (defaultQCState == null)
            props.put(DEFAULT_QC_STATE_KEY, null);
        else
            props.put(DEFAULT_QC_STATE_KEY, defaultQCState.toString());
        props.save();
    }

    @Override
    public List<QCState> getQCStates(Container container)
    {
        if (_states == null)
            _states = QCStateManager.getInstance().getQCStates(container);
        return _states;
    }

    @Override
    public boolean isQCStateInUse(Container container, QCState state)
    {
        // TODO: implement real check
        return false;
    }

    @Override
    public void updateQcState(Container container, CoreController.ManageQCStatesForm form, User user)
    {
        if (!QCStateHandler.nullSafeEqual(getDefaultQCState(container), form.getDefaultQCState()) ||
                isBlankQCStatePublic(container) != form.isBlankQCStatePublic())
        {
            setProps(container, form.isBlankQCStatePublic(), form.getDefaultQCState());
        }
    }
}
