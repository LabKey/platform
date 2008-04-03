package org.labkey.experiment.samples;

import org.labkey.api.data.Container;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerManager;
import org.labkey.experiment.samples.UploadMaterialSetForm.OverwriteChoice;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.Material;
import org.labkey.experiment.api.MaterialSource;
import org.labkey.common.tools.TabLoader;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.util.*;
import java.sql.SQLException;
import java.sql.Timestamp;

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
                TabLoader.ColumnDescriptor[] cds = tl.getColumns();
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

            TabLoader.ColumnDescriptor[] columns = tl.getColumns();
            Map<String, PropertyDescriptor> descriptorsByName = new LinkedHashMap<String, PropertyDescriptor>();
            Map<String, PropertyDescriptor> descriptorsByCaption = new HashMap<String, PropertyDescriptor>();

            PropertyDescriptor[] pds = OntologyManager.getPropertiesForType(materialSourceLsid, getContainer());
            for (PropertyDescriptor pd : pds)
            {
                descriptorsByName.put(pd.getName(), pd);
                if (pd.getLabel() == null)
                {
                    descriptorsByCaption.put(ColumnInfo.captionFromName(pd.getName()), pd);
                }
                else
                {
                    descriptorsByCaption.put(pd.getLabel(), pd);
                }
            }

            DomainDescriptor dd = new DomainDescriptor();
            dd.setDomainURI(materialSourceLsid);
            dd.setContainer(getContainer());

            //TODO: Consider using same id for all properties in same container with same name from user...
            //String basePropertyLsid = new Lsid("Property", lsidPath + "." + setName, "").toString();

            //TODO: Not clear we really want a transaction open all this time.
            //Perhaps just cleanup manually.

            for (int i = 0; i < columns.length; i++)
            {
                TabLoader.ColumnDescriptor cd = columns[i];
                PropertyDescriptor pd = descriptorsByName.get(cd.name);
                if (pd == null)
                {
                    pd = descriptorsByCaption.get(cd.name);
                }
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
                    descriptorsByName.put(pd.getName(), pd);
                }
                cd.name = pd.getPropertyURI();
            }

            String idColName1;
            String idColName2 = null;
            String idColName3 = null;
            List<String> idColPropertyURIs = new ArrayList<String>();
            if (source != null)
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
                if (_form.getOverwriteChoiceEnum() == OverwriteChoice.ignore)
                {
                    List<Map<String, Object>> newMaps = new ArrayList();
                    for (Map<String, Object> map : maps)
                    {
                        String lsid = source.getMaterialLSIDPrefix() + decideName(map, idColPropertyURIs);
                        Material material = ExperimentServiceImpl.get().getMaterial(lsid);
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
                        String lsid = source.getMaterialLSIDPrefix() + decideName(map, idColPropertyURIs);
                        Material material = ExperimentServiceImpl.get().getMaterial(lsid);
                        if (material != null)
                        {
                            if (!material.getContainer().equals(getContainer().getId()))
                            {
                                throw new SQLException("A material with LSID " + lsid + " is already loaded into the folder " + ContainerManager.getForId(material.getContainer()).getPath());
                            }
                            OntologyManager.deleteOntologyObject(_form.getContainer().getId(), material.getLSID());
                            reusedMaterialLSIDs.add(lsid);
                        }
                    }
                }

            }
            insertTabDelimitedMaterial(maps, descriptorsByName.values().toArray(new PropertyDescriptor[0]), idColPropertyURIs, source.getMaterialLSIDPrefix(), source.getLSID(), reusedMaterialLSIDs);
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
            throws SQLException
    {
        if (rows.length == 0)
            return;

        Container c = getContainer();

        //Parent object is the MaterialSet type
        int ownerObjectId = OntologyManager.ensureObject(c.getId(), cpasType);
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
