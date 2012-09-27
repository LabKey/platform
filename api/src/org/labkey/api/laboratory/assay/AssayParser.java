package org.labkey.api.laboratory.assay;

import org.json.JSONObject;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.ValidationException;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 9/15/12
 * Time: 10:40 AM
 */
public interface AssayParser
{
    /**
     * Parses the provided file and json object, returning a list of row maps.
     * @param json
     * @param file
     * @param fileName
     * @param ctx
     * @return
     * @throws ValidationException
     * @throws ExperimentException
     */
    public JSONObject getPreview(JSONObject json, File file, String fileName, ViewContext ctx) throws ValidationException, ExperimentException;

    /**
     * Parses the provided file and json object using getPreview(), then saves this to the database
     * @param json
     * @param file
     * @param fileName
     * @param ctx
     * @throws ValidationException
     * @throws ExperimentException
     */
    public void saveBatch(JSONObject json, File file, String fileName, ViewContext ctx) throws ValidationException, ExperimentException;

    public List<Map<String, Object>> parseResults(JSONObject json, File file) throws ValidationException, ExperimentException;

    public ExpProtocol getProtocol();

    public AssayProvider getProvider();
}
