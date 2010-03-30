package org.labkey.filecontent;

import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewContext;
import org.labkey.filecontent.designer.client.FilePropertiesService;

import java.sql.SQLException;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Mar 25, 2010
 * Time: 12:50:26 PM
 * To change this template use File | Settings | File Templates.
 */
public class FilePropertiesServiceImpl extends DomainEditorServiceBase implements FilePropertiesService
{
    public FilePropertiesServiceImpl(ViewContext context)
    {
        super(context);
    }

    public List<String> updateDomainDescriptor(GWTDomain orig, GWTDomain update)
    {
        try {
            if (orig.getDomainURI() != null)
            {
                DomainDescriptor dd = OntologyManager.ensureDomainDescriptor(orig.getDomainURI(), orig.getName(), getContainer());
                orig.setDomainId(dd.getDomainId());
                orig.setContainer(getContainer().getId());
                
                return super.updateDomainDescriptor(orig, update);
            }
            else
                throw new IllegalArgumentException("DomainURI cannot be null");
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }
        catch (ChangePropertyDescriptorException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }
}
