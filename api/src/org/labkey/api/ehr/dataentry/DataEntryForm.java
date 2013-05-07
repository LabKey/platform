package org.labkey.api.ehr.dataentry;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.template.ClientDependency;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 4/27/13
 * Time: 8:34 AM
 */
public interface DataEntryForm
{
    abstract public String getName();

    abstract public String getLabel();

    abstract public String getCategory();

    abstract public boolean hasPermission(Container c, User u);

    /**
     * Intended for checks like testing whether an owning module is active
     */
    abstract public boolean isAvailable(Container c, User u);

    abstract public String getJavascriptClass();

    abstract public JSONObject toJSON(Container c, User u);

    abstract public List<FormSection> getFormSections();

    abstract public LinkedHashSet<ClientDependency> getClientDependencies();
}
