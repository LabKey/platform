package org.labkey.elispot;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.User;
import org.labkey.api.study.*;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.elispot.plate.ElispotPlateReaderService;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jan 8, 2008
 */
public class ElispotDataHandler extends AbstractExperimentDataHandler
{
    public static final DataType ELISPOT_DATA_TYPE = new DataType("ElispotAssayData");

    public static final String ELISPOT_DATA_LSID_PREFIX = "ElispotAssayData";
    public static final String ELISPOT_DATA_ROW_LSID_PREFIX = "ElispotAssayDataRow";
    public static final String ELISPOT_PROPERTY_LSID_PREFIX = "ElispotProperty";
    public static final String ELISPOT_INPUT_MATERIAL_DATA_PROPERTY = "SpecimenLsid";

    public static final String SFU_PROPERTY_NAME = "SpotCount";
    public static final String WELLGROUP_PROPERTY_NAME = "WellgroupName";

    public void importFile(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context) throws ExperimentException
    {
        ExpRun run = data.getRun();
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(run.getProtocol().getLSID());
        Container container = data.getContainer();
        AssayProvider provider = AssayService.get().getProvider(protocol);
        PlateTemplate template = provider.getPlateTemplate(container, protocol);

        for (ObjectProperty property : run.getObjectProperties().values())
        {
            if (ElispotAssayProvider.READER_PROPERTY_NAME.equals(property.getName()))
            {
                ElispotPlateReaderService.I reader = getPlateReaderFromName(property.getStringValue(), info.getContainer());
                Plate plate = initializePlate(dataFile, template, reader);

                insertPlateData(data, info, plate);
                return;
            }
        }
        throw new ExperimentException("Unable to load data file: Plate reader type not found");
    }

    public static ElispotPlateReaderService.I getPlateReaderFromName(String readerName, Container c)
    {
        ListDefinition list = ElispotPlateReaderService.getPlateReaderList(c);
        if (list != null)
        {
            DomainProperty prop = list.getDomain().getPropertyByName(ElispotPlateReaderService.READER_TYPE_PROPERTY);
            ListItem item = list.getListItem(readerName);
            if (item != null && prop != null)
            {
                Object value = item.getProperty(prop);
                return ElispotPlateReaderService.getPlateReader(String.valueOf(value));
            }
        }
        return null;
    }

    public static Plate initializePlate(File dataFile, PlateTemplate template, ElispotPlateReaderService.I reader) throws ExperimentException
    {
        if (reader != null)
        {
            double[][] cellValues = reader.loadFile(template, dataFile);
            return PlateService.get().createPlate(template, cellValues);
        }
        return null;
    }
    
    private void insertPlateData(ExpData data, ViewBackgroundInfo info, Plate plate) throws ExperimentException
    {
        ExpRun run = data.getRun();
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(run.getProtocol().getLSID());
        Container container = data.getContainer();
        AssayProvider provider = AssayService.get().getProvider(protocol);

        try
        {
            List<ObjectProperty> results = new ArrayList<ObjectProperty>();
            Map<String, ExpMaterial> materialMap = new HashMap<String, ExpMaterial>();
            for (ExpMaterial material : run.getMaterialInputs().keySet())
                materialMap.put(material.getName(), material);

            for (WellGroup group : plate.getWellGroups(WellGroup.Type.SPECIMEN))
            {
                for (Position pos : group.getPositions())
                {
                    results.clear();
                    Well well = plate.getWell(pos.getRow(), pos.getColumn());
                    ExpMaterial material = materialMap.get(group.getName());
                    if (material != null)
                    {
                        Lsid dataRowLsid = getDataRowLsid(data.getLSID(), pos);

                        results.add(getResultObjectProperty(container, protocol, dataRowLsid.toString(), ELISPOT_INPUT_MATERIAL_DATA_PROPERTY, material.getLSID(), PropertyType.STRING));
                        results.add(getResultObjectProperty(container, protocol, dataRowLsid.toString(), SFU_PROPERTY_NAME, well.getValue(), PropertyType.DOUBLE, "0.0"));
                        results.add(getResultObjectProperty(container, protocol, dataRowLsid.toString(), "WellgroupName", group.getName(), PropertyType.STRING));
                        results.add(getResultObjectProperty(container, protocol, dataRowLsid.toString(), "WellLocation", pos.toString(), PropertyType.STRING));

                        OntologyManager.ensureObject(container.getId(), dataRowLsid.toString(),  data.getLSID());
                        OntologyManager.insertProperties(container.getId(), results.toArray(new ObjectProperty[results.size()]), dataRowLsid.toString());
                    }
                }
            }
        }
        catch (SQLException se)
        {
            throw new ExperimentException(se);
        }
    }

    public static Lsid getDataRowLsid(String dataLsid, Position pos)
    {
        return getDataRowLsid(dataLsid, pos.getRow(), pos.getColumn());
    }

    public static Lsid getDataRowLsid(String dataLsid, int row, int col)
    {
        Lsid dataRowLsid = new Lsid(dataLsid);
        dataRowLsid.setNamespacePrefix(ELISPOT_DATA_ROW_LSID_PREFIX);
        dataRowLsid.setObjectId(dataRowLsid.getObjectId() + "-" + row + ':' + col);

        return dataRowLsid;
    }

    public static ObjectProperty getResultObjectProperty(Container container, ExpProtocol protocol, String objectURI,
                                                   String propertyName, Object value, PropertyType type)
    {
        return getResultObjectProperty(container, protocol, objectURI, propertyName, value, type, null);
    }

    public static ObjectProperty getResultObjectProperty(Container container, ExpProtocol protocol, String objectURI,
                                                   String propertyName, Object value, PropertyType type, String format)
    {
        Lsid propertyURI = new Lsid(ELISPOT_PROPERTY_LSID_PREFIX, protocol.getName(), propertyName);
        ObjectProperty prop = new ObjectProperty(objectURI, container.getId(), propertyURI.toString(), value, type, propertyName);
        prop.setFormat(format);
        return prop;
    }

    public URLHelper getContentURL(Container container, ExpData data)
    {
        return null;
    }

    public void deleteData(ExpData data, Container container, User user) throws ExperimentException
    {
        try {
            OntologyManager.deleteOntologyObject(data.getLSID(), container, true);
        }
        catch (SQLException e)
        {
            throw new ExperimentException(e);
        }
    }

    public void runMoved(ExpData newData, Container container, Container targetContainer, String oldRunLSID, String newRunLSID, User user, int oldDataRowID) throws ExperimentException
    {
        throw new UnsupportedOperationException();
    }

    public Priority getPriority(ExpData data)
    {
        Lsid lsid = new Lsid(data.getLSID());
        if (ELISPOT_DATA_LSID_PREFIX.equals(lsid.getNamespacePrefix()))
        {
            return Priority.HIGH;
        }
        return null;
    }
}
