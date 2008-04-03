package org.labkey.study.plate;

import org.labkey.plate.designer.client.PlateDataService;
import org.labkey.plate.designer.client.model.GWTPlate;
import org.labkey.plate.designer.client.model.GWTPosition;
import org.labkey.plate.designer.client.model.GWTWellGroup;
import org.labkey.api.study.*;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.gwt.client.util.ExceptionUtil;
import org.labkey.api.view.ViewContext;
import java.sql.SQLException;
import java.util.*;

import com.google.gwt.user.client.rpc.SerializableException;

/**
 * User: brittp
 * Date: Jan 31, 2007
 * Time: 2:38:17 PM
 */
public class PlateDataServiceImpl extends BaseRemoteService implements PlateDataService
{
    public PlateDataServiceImpl(ViewContext context)
    {
        super(context);
    }

    public GWTPlate getTemplateDefinition(String templateName, String assayTypeName, String templateTypeName) throws SerializableException
    {
        try
        {
            PlateTemplate template;
            if (templateName == null)  // If new PlateTemplate, get default
            {
                PlateTypeHandler handler = PlateManager.get().getPlateTypeHandler(assayTypeName);
                if (handler == null)
                {
                    throw new SerializableException("Plate template type " + assayTypeName + " does not exist.");
                }
                template = handler.createPlate(templateTypeName, getContainer());
            }
            else  // If its already created, get PlateTemplate from database
            {
                template = PlateService.get().getPlateTemplate(getContainer(), templateName);
                if (template == null)
                    throw new SerializableException("Plate " + templateName + " does not exist.");
            }

            // Translate PlateTemplate to GWTPlate
            List<? extends WellGroupTemplate> groups = template.getWellGroups();
            List<GWTWellGroup> translated = new ArrayList<GWTWellGroup>();
            for (WellGroupTemplate group : groups)
            {
                List<GWTPosition> positions = new ArrayList<GWTPosition>(group.getPositions().size());
                for (Position position : group.getPositions())
                    positions.add(new GWTPosition(position.getRow(), position.getColumn()));
                Map<String, Object> groupProperties = new HashMap<String, Object>();
                for (String propName : group.getPropertyNames())
                {
                    groupProperties.put(propName, group.getProperty(propName));

                }
                translated.add(new GWTWellGroup(group.getType().name(), group.getName(), positions, groupProperties));
            }
            GWTPlate plate = new GWTPlate(template.getName(), template.getType(), template.getRows(),
                    template.getColumns(), getTypeList(template));
            plate.setGroups(translated);
            Map<String, String> templateProperties = new HashMap<String, String>();
            for (String propName : template.getPropertyNames())
            {
                templateProperties.put(propName, template.getProperty(propName) == null ? null : template.getProperty(propName).toString());
            }
            plate.setPlateProperties(templateProperties);
            return plate;
        }
        catch (SQLException e)
        {
            throw ExceptionUtil.convertToSerializable(e);
        }
    }

    private List<String> getTypeList(PlateTemplate template)
    {
        List<String> types = new ArrayList<String>();
        WellGroup.Type[] wellTypes = new WellGroup.Type[]{
                WellGroup.Type.CONTROL, WellGroup.Type.SPECIMEN,
                WellGroup.Type.REPLICATE, WellGroup.Type.OTHER};

        PlateTypeHandler handler = PlateManager.get().getPlateTypeHandler(template.getType());
        if (handler != null)
            wellTypes = handler.getWellGroupTypes();

        for (WellGroup.Type type : wellTypes)
            types.add(type.name());
        return types;
    }

    public void saveChanges(GWTPlate gwtPlate, boolean replaceIfExisting) throws SerializableException
    {
        try
        {
            PlateTemplate template = PlateService.get().getPlateTemplate(getContainer(), gwtPlate.getName());
            if (template != null)
            {
                if (replaceIfExisting)
                    PlateService.get().deletePlate(getContainer(), template.getRowId());
                else
                    throw new SerializableException("A plate with name '" + gwtPlate.getName() + "' already exists.");
            }

            template = PlateService.get().createPlateTemplate(getContainer(), gwtPlate.getType());
            template.setName(gwtPlate.getName());
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) gwtPlate.getPlateProperties()).entrySet())
                template.setProperty(entry.getKey(), entry.getValue());

            List<GWTWellGroup> groups = (List<GWTWellGroup>) gwtPlate.getGroups();
            for (GWTWellGroup gwtGroup : groups)
            {
                List<Position> positions = new ArrayList<Position>();
                for (GWTPosition gwtPosition : (List<GWTPosition>) gwtGroup.getPositions())
                    positions.add(template.getPosition(gwtPosition.getRow(), gwtPosition.getCol()));

                if (!positions.isEmpty())
                {
                    WellGroupTemplate group = template.addWellGroup(gwtGroup.getName(), WellGroup.Type.valueOf(gwtGroup.getType()), positions);

                    for (Map.Entry<String, Object> entry : ((Map<String, Object>) gwtGroup.getProperties()).entrySet())
                        group.setProperty(entry.getKey(), entry.getValue());
                }
            }
            PlateService.get().save(getContainer(), getUser(), template);
        }
        catch (SQLException e)
        {
            throw ExceptionUtil.convertToSerializable(e);
        }
    }
}
