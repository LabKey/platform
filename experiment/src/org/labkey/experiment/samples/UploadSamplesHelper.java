/*
 * Copyright (c) 2006-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.experiment.samples;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.*;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.CaseInsensitiveHashSet;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.common.tools.TabLoader;
import org.labkey.common.tools.ColumnDescriptor;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.MaterialSource;
import org.labkey.experiment.api.ExpMaterialImpl;
import org.labkey.experiment.samples.UploadMaterialSetForm.OverwriteChoice;
import org.apache.log4j.Logger;

import java.sql.SQLException;
import java.util.*;
import java.io.IOException;

public class UploadSamplesHelper
{
    private static final Logger _log = Logger.getLogger(UploadSamplesHelper.class);

    UploadMaterialSetForm _form;

    public UploadSamplesHelper(UploadMaterialSetForm form)
    {
        _form = form;
    }

    public Map<Integer, String> getIdFieldOptions(boolean allowBlank)
    {
        if (_form.getData() != null)
        {
            try
            {
                TabLoader tl = new TabLoader(_form.getData(), true);
                Map<Integer, String> ret = new LinkedHashMap<Integer, String>();
                if (allowBlank)
                {
                    ret.put(-1, "");
                }
                ColumnDescriptor[] cds = tl.getColumns();
                for (int i = 0; i < cds.length; i ++)
                {
                    ret.put(i, cds[i].name);
                }
                return ret;
            }
            catch (Exception e)
            {
                // ignore
            }
        }
        return Collections.singletonMap(0, "<Please paste data>");
    }

    public Container getContainer()
    {
        return _form.getContainer();
    }

    public MaterialSource uploadMaterials() throws ExperimentException, ValidationException, IOException
    {
        MaterialSource source;
        try
        {
            String input = _form.getData();
            TabLoader tl = new TabLoader(input, true);
            tl.setScanAheadLineCount(200);
            String materialSourceLsid = ExperimentService.get().getSampleSetLsid(_form.getName(), _form.getContainer()).toString();
            source = ExperimentServiceImpl.get().getMaterialSource(materialSourceLsid);
            ExperimentService.get().getSchema().getScope().beginTransaction();

            ColumnDescriptor[] columns = tl.getColumns();

            PropertyDescriptor[] pds = OntologyManager.getPropertiesForType(materialSourceLsid, getContainer());
            Map<String, PropertyDescriptor> descriptorsByName = OntologyManager.createImportPropertyMap(pds);
            ArrayList<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>(Arrays.asList(pds));
            
            DomainDescriptor dd = new DomainDescriptor();
            dd.setDomainURI(materialSourceLsid);
            dd.setContainer(getContainer());

            for (ColumnDescriptor cd : columns)
            {
                PropertyDescriptor pd = descriptorsByName.get(cd.name);
                if (pd == null || source == null)
                {
                    pd = new PropertyDescriptor();
                    //todo :  name for domain?
                    pd.setName(cd.name);
                    String legalName = ColumnInfo.legalNameFromName(cd.name);
                    String propertyURI = materialSourceLsid + "#" + legalName;
                    pd.setPropertyURI(propertyURI);
                    pd.setRangeURI(PropertyType.getFromClass(cd.clazz).getTypeUri());
                    pd.setContainer(_form.getContainer());
                    //Change name to be fully qualified string for property
                    pd = OntologyManager.insertOrUpdatePropertyDescriptor(pd, dd);
                    descriptors.add(pd);
                    descriptorsByName.put(pd.getName(), pd);
                }
                cd.name = pd.getPropertyURI();
            }

            List<String> idColPropertyURIs;
            if (source != null && source.getIdCol1() != null)
            {
                idColPropertyURIs = getIdColPropertyURIs(source);
            }
            else
            {
                idColPropertyURIs = new ArrayList<String>();
                idColPropertyURIs.add(tl.getColumns()[_form.getIdColumn1()].name);
                if (_form.getIdColumn2() >= 0)
                {
                    idColPropertyURIs.add(tl.getColumns()[_form.getIdColumn2()].name);
                }
                if (_form.getIdColumn3() >= 0)
                {
                    idColPropertyURIs.add(tl.getColumns()[_form.getIdColumn3()].name);
                }
            }
            String parentColPropertyURI;
            if (source != null && source.getParentCol() != null)
            {
                parentColPropertyURI = source.getParentCol();
            }
            else if (_form.getParentColumn() >= 0)
            {
                parentColPropertyURI = tl.getColumns()[_form.getParentColumn()].name;
            }
            else
            {
                parentColPropertyURI = null;
            }

            List<Map<String, Object>> maps = tl.load();

            if (maps.size() > 0)
            {
                for (String uri : idColPropertyURIs)
                {
                    if (!maps.get(0).containsKey(uri))
                    {
                        throw new ExperimentException("Id Columns must match");
                    }
                    for (int i = 0; i < maps.size(); i++)
                    {
                        if (maps.get(i).get(uri) == null)
                        {
                            if (uri.contains("#"))
                            {
                                uri = uri.substring(uri.indexOf("#") + 1);
                            }
                            throw new ExperimentException("All rows must contain values for all Id columns:  Missing " + uri +
                                " in row:  " + i);
                        }
                    }
                }
            }

            Set<String> newNames = new CaseInsensitiveHashSet();
            for (Map<String, Object> map : maps)
            {
                String name = decideName(map, idColPropertyURIs);
                if (!newNames.add(name))
                {
                    throw new ExperimentException("Duplicate material: " + name);
                }
            }

            Set<String> reusedMaterialLSIDs = new HashSet<String>();
            if (source == null)
            {
                source = new MaterialSource();
                String setName = PageFlowUtil.encode(_form.getName());
                source.setContainer(_form.getContainer());
                source.setDescription("Samples uploaded by " + _form.getUser().getEmail());
                Lsid lsid = ExperimentServiceImpl.get().getSampleSetLsid(_form.getName(), _form.getContainer());
                source.setLSID(lsid.toString());
                source.setName(_form.getName());
                setCols(idColPropertyURIs, parentColPropertyURI, source);
                source.setMaterialLSIDPrefix(new Lsid("Sample", String.valueOf(_form.getContainer().getRowId()) + "." + setName, "").toString());
                source = ExperimentServiceImpl.get().insertMaterialSource(_form.getUser(), source, dd);
            }
            else
            {
                // 6088: update id cols for already existing material source if none have been set
                if (source.getIdCol1() == null || (source.getParentCol() == null && parentColPropertyURI != null))
                {
                    assert source.getName().equals(_form.getName());
                    assert source.getLSID().equals(ExperimentServiceImpl.get().getSampleSetLsid(_form.getName(), _form.getContainer()).toString());
                    setCols(idColPropertyURIs, parentColPropertyURI, source);
                    source = ExperimentServiceImpl.get().updateMaterialSource(_form.getUser(), source);
                }

                if (maps.size() > 0)
                {
                    Set<String> uploadedPropertyURIs = maps.get(0).keySet();
                    if (!uploadedPropertyURIs.containsAll(idColPropertyURIs))
                    {
                        throw new ExperimentException("Your upload must contain the original id columns");
                    }
                }
                if (_form.getOverwriteChoiceEnum() == OverwriteChoice.ignore)
                {
                    List<Map<String, Object>> newMaps = new ArrayList<Map<String, Object>>();
                    for (Map<String, Object> map : maps)
                    {
                        String lsid = new Lsid(source.getMaterialLSIDPrefix() + decideName(map, idColPropertyURIs)).toString();
                        ExpMaterial material = ExperimentService.get().getExpMaterial(lsid);
                        if (material == null)
                        {
                            newMaps.add(map);
                        }
                    }
                    maps = newMaps;
                }
                else if (_form.getOverwriteChoiceEnum() == OverwriteChoice.replace)
                {
                    for (Map<String, Object> map : maps)
                    {
                        String lsid = new Lsid(source.getMaterialLSIDPrefix() + decideName(map, idColPropertyURIs)).toString();
                        ExpMaterial material = ExperimentService.get().getExpMaterial(lsid);
                        if (material != null)
                        {
                            if (!material.getContainer().equals(getContainer()))
                            {
                                throw new SQLException("A material with LSID " + lsid + " is already loaded into the folder " + material.getContainer().getPath());
                            }
                            OntologyManager.deleteOntologyObjects(_form.getContainer(), material.getLSID());
                            reusedMaterialLSIDs.add(lsid);
                        }
                    }
                }
            }
            insertTabDelimitedMaterial(maps, descriptors.toArray(new PropertyDescriptor[descriptors.size()]), source, reusedMaterialLSIDs);
            _form.getSampleSet().onSamplesChanged(_form.getUser(), null);
            ExperimentService.get().getSchema().getScope().commitTransaction();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            ExperimentService.get().getSchema().getScope().closeConnection();
        }
        return source;
    }

    private List<String> getIdColPropertyURIs(MaterialSource source)
    {
        List<String> idColNames = new ArrayList<String>();
        idColNames.add(source.getIdCol1());
        if (source.getIdCol2() != null)
        {
            idColNames.add(source.getIdCol2());
        }
        if (source.getIdCol3() != null)
        {
            idColNames.add(source.getIdCol3());
        }
        return idColNames;
    }

    private void setCols(List<String> idColPropertyURIs, String parentColPropertyURI, MaterialSource source)
    {
        assert idColPropertyURIs.size() <= 3 : "Found " + idColPropertyURIs.size() + " id cols but 3 is the limit";
        source.setIdCol1(idColPropertyURIs.get(0));
        if (idColPropertyURIs.size() > 1)
        {
            source.setIdCol2(idColPropertyURIs.get(1));
            if (idColPropertyURIs.size() > 2)
            {
                source.setIdCol3(idColPropertyURIs.get(2));
            }
        }
        if (parentColPropertyURI != null)
        {
            source.setParentCol(parentColPropertyURI);
        }
    }

    protected String decideName(Map<String, Object> rowMap, List<String> idCols)
    {
        StringBuilder ret = new StringBuilder(String.valueOf(rowMap.get(idCols.get(0))));
        for (int i = 1; i < idCols.size(); i ++)
        {
            String col = idCols.get(i);
            ret.append("-");
            Object value = rowMap.get(col);
            if (value != null)
            {
                ret.append(value);
            }
        }
        return ret.toString();
    }

    public void insertTabDelimitedMaterial(List<Map<String, Object>> rows, PropertyDescriptor[] descriptors, MaterialSource source, Set<String> reusedMaterialLSIDs)
            throws SQLException, ValidationException, ExperimentException
    {
        if (rows.size() == 0)
            return;

        Container c = getContainer();

        //Parent object is the MaterialSet type
        int ownerObjectId = OntologyManager.ensureObject(c, source.getLSID());
        MaterialImportHelper helper = new MaterialImportHelper(c, source, _form.getUser(), reusedMaterialLSIDs);

        OntologyManager.insertTabDelimited(c, ownerObjectId, helper, descriptors, rows, false);

        if (source.getParentCol() != null)
        {
            // Map from material name to material of all materials in all sample sets visible from this location
            Map<String, List<ExpMaterialImpl>> potentialParents = ExperimentServiceImpl.get().getSamplesByName(_form.getContainer(), _form.getUser());

            assert rows.size() == helper._materials.size() : "Didn't find as many materials as we have rows";
            for (int i = 0; i < rows.size(); i++)
            {
                Map<String, Object> row = rows.get(i);
                ExpMaterial material = helper._materials.get(i);

                if (reusedMaterialLSIDs.contains(material.getLSID()))
                {
                    // Since this entry was already in the database, we may need to delete old derivation info
                    ExpProtocolApplication existingSourceApp = material.getSourceApplication();
                    if (existingSourceApp != null)
                    {
                        ExpRun existingDerivationRun = existingSourceApp.getRun();
                        if (existingDerivationRun != null)
                        {
                            material.setSourceApplication(null);
                            material.save(_form.getUser());
                            existingDerivationRun.delete(_form.getUser());
                        }
                    }
                }

                String newParent = row.get(source.getParentCol()) == null ? null : row.get(source.getParentCol()).toString();
                if (newParent != null)
                {
                    // Need to create a new derivation run
                    List<ExpMaterial> parentMaterials = resolveParentMaterials(newParent, potentialParents);
                    Map<ExpMaterial, String> parentMap = new HashMap<ExpMaterial, String>();
                    int index = 1;
                    for (ExpMaterial parentMaterial : parentMaterials)
                    {
                        parentMap.put(parentMaterial, "Sample" + (index == 1 ? "" : Integer.toString(index)));
                        index++;
                    }
                    ExperimentService.get().deriveSamples(parentMap, Collections.singletonMap(material, "Sample"), new ViewBackgroundInfo(_form.getContainer(),  _form.getUser(), null), _log);
                }
            }
        }
    }

    private List<ExpMaterial> resolveParentMaterials(String newParent, Map<String, List<ExpMaterialImpl>> materials) throws ValidationException, ExperimentException
    {
        List<ExpMaterial> parents = new ArrayList<ExpMaterial>();

        String[] parentNames = newParent.split(",");
        for (String parentName : parentNames)
        {
            parentName = parentName.trim();
            List<? extends ExpMaterial> potentialParents = materials.get(parentName);
            if (potentialParents != null && potentialParents.size() == 1)
            {
                parents.add(potentialParents.get(0));
            }
            else
            {
                ExpMaterial parent = null;
                // Couldn't find exactly one match, check if it might be of the form <SAMPLE_SET_NAME>.<SAMPLE_NAME>
                int dotIndex = parentName.indexOf(".");
                if (dotIndex != -1)
                {
                    String sampleSetName = parentName.substring(0, dotIndex);
                    String sampleName = parentName.substring(dotIndex + 1);
                    parent = findParent(sampleSetName, sampleName);
                }
                if (parent != null)
                {
                    parents.add(parent);
                }
                else if (potentialParents == null)
                {
                    throw new ExperimentException("Could not find parent material with name '" + parentName + "'.");
                }
                else if (potentialParents.size() > 1)
                {
                    throw new ExperimentException("More than one match for parent material '" + parentName + "' was found.");
                }
            }
        }
        return parents;
    }

    private ExpMaterial findParent(String sampleSetName, String sampleName)
    {
        // Could easily do some caching here, but probably not a significant perf issue
        ExpSampleSet[] sampleSets = ExperimentService.get().getSampleSets(getContainer(), _form.getUser(), true);
        for (ExpSampleSet sampleSet : sampleSets)
        {
            // Look for a sample set with the right name
            if (sampleSetName.equals(sampleSet.getName()))
            {
                for (ExpMaterial sample : sampleSet.getSamples())
                {
                    // Look for a sample with the right name
                    if (sample.getName().equals(sampleName))
                    {
                        return sample;
                    }
                }
            }
        }
        return null;
    }

    private class MaterialImportHelper implements OntologyManager.ImportHelper
    {
        private Container _container;
        private List<String> _idCols;
        private User _user;
        private MaterialSource _source;
        private final Set<String> _reusedMaterialLSIDs;
        private List<ExpMaterial> _materials = new ArrayList<ExpMaterial>();

        MaterialImportHelper(Container container, MaterialSource source, User user, Set<String> reusedMaterialLSIDs)
        {
            _container = container;
            _idCols = getIdColPropertyURIs(source);
            _source = source;
            _user = user;
            _reusedMaterialLSIDs = reusedMaterialLSIDs;
        }

        public String beforeImportObject(Map<String, Object> map) throws SQLException
        {
            String name = decideName(map, _idCols);
            String lsid = new Lsid(_source.getMaterialLSIDPrefix() + name).toString();

            ExpMaterial material;
            if (!_reusedMaterialLSIDs.contains(lsid))
            {
                material = ExperimentService.get().createExpMaterial(_container, lsid, name);
                material.setCpasType(_source.getLSID());
                material.save(_user);
            }
            else
            {
                material = ExperimentService.get().getExpMaterial(lsid);
                assert material != null : "Could not find existing material with lsid " + lsid;
            }
            _materials.add(material);

            return lsid;
        }

        public void afterImportObject(String lsid, ObjectProperty[] props) throws SQLException
        {
        }
    }


}
