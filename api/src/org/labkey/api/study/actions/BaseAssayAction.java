package org.labkey.api.study.actions;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.ACL;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jul 26, 2007
 * Time: 7:01:48 PM
 */
public abstract class BaseAssayAction<T extends ProtocolIdForm> extends SimpleViewAction<T>
{
    public BaseAssayAction()
    {
        super();
    }

    public BaseAssayAction(Class<T> formClass)
    {
        super(formClass);
    }

    protected ActionURL getUrl(String action)
    {
        ActionURL copy = getViewContext().cloneActionURL();
        copy.deleteParameters();
        copy.setAction(action);
        return copy;
    }

    public ActionURL getSummaryLink(ExpProtocol protocol)
    {
        return AssayService.get().getAssayRunsURL(getContainer(), protocol);
    }

    public static ExpProtocol getProtocol(ProtocolIdForm form)
    {
        return getProtocol(form, true);
    }

    public static ExpProtocol getProtocol(ProtocolIdForm form, boolean validateContainer)
    {
        if (form.getRowId() == null)
            HttpView.throwNotFound("Assay ID not specified.");
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(form.getRowId());
        if (protocol == null || (validateContainer && !protocol.getContainer().equals(form.getContainer()) &&
                !protocol.getContainer().equals(form.getContainer().getProject())))
        {
            HttpView.throwNotFound("Assay " + form.getRowId() + " does not exist.");
        }
        if (protocol != null)
        {
            // even if we don't validate that the protocol is from the current or project container,
            // but we still make sure that the current user can read from the protocol container:
            if (!protocol.getContainer().hasPermission(form.getViewContext().getUser(), ACL.PERM_READ))
                HttpView.throwNotFound();
        }
        return protocol;
    }

    protected Container getContainer()
    {
        return getViewContext().getContainer();
    }

    protected DataRegion createDataRegion(TableInfo baseTable, String lsidCol, PropertyDescriptor[] propertyDescriptors, Map<String, String> columnNameToPropertyName, String uploadStepName)
    {
        DataRegion rgn = new DataRegion();
        rgn.setTable(baseTable);
        for (PropertyDescriptor pd : propertyDescriptors)
        {
            ColumnInfo info = pd.createColumnInfo(baseTable, lsidCol, getViewContext().getUser());
            rgn.addColumn(info);
            if (columnNameToPropertyName != null)
                columnNameToPropertyName.put(info.getName(), pd.getName());
        }
        return rgn;
    }

    protected AssayProvider getProvider(ProtocolIdForm form)
    {
        return AssayService.get().getProvider(getProtocol(form));
    }

    public NavTree appendNavTrail(NavTree root)
    {
        return root.addChild("Assay List", new ActionURL("assay", "begin.view", getContainer()));
    }

    protected List<Integer> getCheckboxIds(boolean clear)
    {
        Set<String> idStrings =  DataRegionSelection.getSelected(getViewContext(), clear);
        List<Integer> ids = new ArrayList<Integer>();
        if (idStrings == null)
            return ids;
        for (String rowIdStr : idStrings)
            ids.add(Integer.parseInt(rowIdStr));
        return ids;
    }
}
