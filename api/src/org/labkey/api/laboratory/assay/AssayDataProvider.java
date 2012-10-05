package org.labkey.api.laboratory.assay;

import org.labkey.api.laboratory.DataProvider;
import org.labkey.api.study.assay.AssayProvider;

import java.util.Collection;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 9/28/12
 * Time: 4:11 PM
 */

/**
 * This interface is used by LaboratoryService to register assays.  It wraps the core assay description to provide additional
 * details used specifically in the laboratory module
 */
public interface AssayDataProvider extends DataProvider
{
    abstract public String getProviderName();

    abstract public AssayProvider getAssayProvider();

    /**
     * Returns the set of AssayImportMethods supported by this assay.  If none are provided, a default import method
     * will be used
     * @return
     */
    abstract public Collection<AssayImportMethod> getImportMethods();
}
