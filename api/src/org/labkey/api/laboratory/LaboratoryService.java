package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.laboratory.assay.AssayImportMethod;
import org.labkey.api.laboratory.assay.AssayParser;
import org.labkey.api.module.Module;
import org.labkey.api.query.ValidationException;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 9/15/12
 * Time: 6:26 AM
 */
abstract public class LaboratoryService
{
    static LaboratoryService instance;

    public static LaboratoryService get()
    {
        return instance;
    }

    static public void setInstance(LaboratoryService instance)
    {
        LaboratoryService.instance = instance;
    }

    abstract public void registerModule(Module module);

    abstract public Set<Module> getRegisteredModules();

    abstract public void registerAssayImportMethods(String providerName, AssayImportMethod... methodList);

    abstract public List<AssayImportMethod> getImportMethods(AssayProvider ap);

    abstract public AssayImportMethod getImportMethodByName(String assayName, String methodName);

    abstract public Pair<ExpExperiment, ExpRun> saveAssayBatch(AssayParser parser, JSONObject json, File file, String fileName, ViewContext ctx) throws ValidationException, ExperimentException;

    abstract public AssayImportMethod getManualEntryImportMethod();

}
