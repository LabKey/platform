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
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.CaseInsensitiveHashSet;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.common.tools.TabLoader;
import org.labkey.common.tools.ColumnDescriptor;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.Material;
import org.labkey.experiment.api.MaterialSource;
import org.labkey.experiment.samples.UploadMaterialSetForm.OverwriteChoice;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

public class UploadSamplesHelper
{
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

    public MaterialSource uploadMaterials() throws Exception
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

            //TODO: Consider using same id for all properties in same container with same name from user...
            //String basePropertyLsid = new Lsid("Property", lsidPath + "." + setName, "").toString();

            //TODO: Not clear we really want a transaction open all this time.
            //Perhaps just cleanup manually.

            for (int i = 0; i < columns.length; i++)
            {
                ColumnDescriptor cd = columns[i];
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

            String idColName1;
            String idColName2 = null;
            String idColName3 = null;
            List<String> idColPropertyURIs = new ArrayList<String>();
            if (source != null && source.getIdCol1() != null)
            {
                idColName1 = source.getIdCol1();
                idColName2 = source.getIdCol2();
                idColName3 = source.getIdCol3();
            }
            else
            {
                idColName1 = tl.getColumns()[_form.getIdColumn1()].name;
                if (_form.getIdColumn2() >= 0)
                {
                    idColName2 = tl.getColumns()[_form.getIdColumn2()].name;
                }
                if (_form.getIdColumn3() >= 0)
                {
                    idColName3 = tl.getColumns()[_form.getIdColumn3()].name;
                }
            }
            idColPropertyURIs.add(idColName1);
            if (idColName2 != null)
            {
                idColPropertyURIs.add(idColName2);
            }
            if (idColName3 != null)
            {
                idColPropertyURIs.add(idColName3);
            }

            Map<String, Object>[] maps = (Map<String, Object>[]) tl.load();

            if (maps.length > 0)
            {
                for (String uri : idColPropertyURIs)
                {
                    if (!maps[0].containsKey(uri))
                    {
                        throw new ExperimentException("Id Columns must match");
                    }
                    for (int i = 0; i < maps.length; i++)
                    {
                        if (maps[i].get(uri) == null)
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
                source.setContainer(_form.getContainer().getId());
                source.setDescription("Samples uploaded by " + _form.getUser().getEmail());
                Lsid lsid = ExperimentServiceImpl.get().getSampleSetLsid(_form.getName(), _form.getContainer());
                source.setLSID(lsid.toString());
                source.setName(_form.getName());
                source.setIdCol1(idColName1);
                source.setIdCol2(idColName2);
                source.setIdCol3(idColName3);
                source.setMaterialLSIDPrefix(new Lsid("Sample", String.valueOf(_form.getContainer().getRowId()) + "." + setName, "").toString());
                source = ExperimentServiceImpl.get().insertMaterialSource(_form.getUser(), source, dd);
            }
            else
            {
                // 6088: update id cols for already existing material source if none have been set
                if (source.getIdCol1() == null && idColName1 != null)
                {
                    assert source.getName().equals(_form.getName());
                    assert source.getLSID().equals(ExperimentServiceImpl.get().getSampleSetLsid(_form.getName(), _form.getContainer()).toString());
                    source.setIdCol1(idColName1);
                    source.setIdCol2(idColName2);
                    source.setIdCol3(idColName3);
                    source = ExperimentServiceImpl.get().updateMaterialSource(_form.getUser(), source);
                }

                if (maps.length > 0)
                {
                    Set<String> uploadedPropertyURIs = maps[0].keySet();
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
                    maps = newMaps.toArray(new Map[0]);
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
            insertTabDelimitedMaterial(maps, descriptors.toArray(new PropertyDescriptor[descriptors.size()]), idColPropertyURIs, source.getMaterialLSIDPrefix(), source.getLSID(), reusedMaterialLSIDs);
            _form.getSampleSet().onSamplesChanged(_form.getUser(), null);
            ExperimentService.get().getSchema().getScope().commitTransaction();
        }
        finally
        {
            ExperimentService.get().getSchema().getScope().closeConnection();

        }
        return source;
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

    public void insertTabDelimitedMaterial(Map[] rows, PropertyDescriptor[] descriptors, List<String> idCols, String objectPrefix, String cpasType, Set<String> reusedMaterialLSIDs)
            throws SQLException, ValidationException
    {
        if (rows.length == 0)
            return;

        Container c = getContainer();

        //Parent object is the MaterialSet type
        int ownerObjectId = OntologyManager.ensureObject(c, cpasType);
        Timestamp createDate = new Timestamp(System.currentTimeMillis());
        OntologyManager.ImportHelper helper = new MaterialImportHelper(c.getId(), cpasType, createDate, idCols, objectPrefix, _form.getUser(), reusedMaterialLSIDs);

        OntologyManager.insertTabDelimited(c, ownerObjectId, helper, descriptors, rows, false);
    }

    class MaterialImportHelper implements OntologyManager.ImportHelper
    {
        String containerId;
        String cpasType;
        Timestamp createDate;
        List<String> idCols;
        String objectPrefix;
        User user;
        private final Set<String> _reusedMaterialLSIDs;


        MaterialImportHelper(String containerId, String cpasType, Timestamp createDate, List<String> idCols, String objectPrefix, User user, Set<String> reusedMaterialLSIDs)
        {
            this.containerId = containerId;
            this.cpasType = cpasType;
            this.createDate = createDate;
            this.idCols = idCols;
            this.objectPrefix = objectPrefix;
            this.user = user;
            _reusedMaterialLSIDs = reusedMaterialLSIDs;
        }

        public String beforeImportObject(Map map) throws SQLException
        {
            String name = decideName(map, idCols);
            String lsid = new Lsid(objectPrefix + name).toString();

            if (!_reusedMaterialLSIDs.contains(lsid))
            {
                Material mat = new Material();
                mat.setContainer(containerId);
                mat.setCpasType(cpasType);
                mat.setCreated(createDate);

                mat.setName(name);
                mat.setLSID(lsid);

                mat = ExperimentServiceImpl.get().insertMaterial(user, mat);
            }
            return lsid;
        }

        public void afterImportObject(String lsid, ObjectProperty[] props) throws SQLException
        {
        }
    }


}
