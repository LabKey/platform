/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.study.plate;

import gwt.client.org.labkey.plate.designer.client.PlateDataService;
import gwt.client.org.labkey.plate.designer.client.model.GWTPlate;
import gwt.client.org.labkey.plate.designer.client.model.GWTPosition;
import gwt.client.org.labkey.plate.designer.client.model.GWTWellGroup;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.PlateTypeHandler;
import org.labkey.api.study.Position;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.WellGroupTemplate;
import org.labkey.api.view.ViewContext;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public GWTPlate getTemplateDefinition(String templateName, int plateId, String assayTypeName, String templateTypeName, int rowCount, int columnCount) throws Exception
    {
        try
        {
            PlateTemplate template;
            PlateTypeHandler handler;

            if (templateName != null)
            {
                // existing template
                template = PlateService.get().getPlateTemplate(getContainer(), plateId);
                if (template == null)
                    throw new Exception("Plate " + templateName + " does not exist.");

                handler = PlateManager.get().getPlateTypeHandler(template.getType());
                if (handler == null)
                    throw new Exception("Plate template type " + template.getType() + " does not exist.");
            }
            else
            {
                // new default template
                handler = PlateManager.get().getPlateTypeHandler(assayTypeName);
                if (handler == null)
                    throw new Exception("Plate template type " + assayTypeName + " does not exist.");

                template = handler.createPlate(templateTypeName, getContainer(), rowCount, columnCount);
            }
            // Translate PlateTemplate to GWTPlate
            List<? extends WellGroupTemplate> groups = template.getWellGroups();
            List<GWTWellGroup> translated = new ArrayList<>();
            for (WellGroupTemplate group : groups)
            {
                List<GWTPosition> positions = new ArrayList<>(group.getPositions().size());
                for (Position position : group.getPositions())
                    positions.add(new GWTPosition(position.getRow(), position.getColumn()));
                Map<String, Object> groupProperties = new HashMap<>();
                for (String propName : group.getPropertyNames())
                {
                    groupProperties.put(propName, group.getProperty(propName));
                }
                translated.add(new GWTWellGroup(group.getType().name(), group.getName(), positions, groupProperties));
            }
            GWTPlate plate = new GWTPlate(template.getName(), template.getType(), template.getRows(),
                    template.getColumns(), getTypeList(template), handler.showEditorWarningPanel());
            plate.setGroups(translated);
            plate.setTypesToDefaultGroups(handler.getDefaultGroupsForTypes());
            
            Map<String, Object> templateProperties = new HashMap<>();
            for (String propName : template.getPropertyNames())
            {
                templateProperties.put(propName, template.getProperty(propName) == null ? null : template.getProperty(propName).toString());
            }
            plate.setPlateProperties(templateProperties);
            return plate;
        }
        catch (SQLException e)
        {
            throw new Exception(e);
        }
    }

    private List<String> getTypeList(PlateTemplate template)
    {
        List<WellGroup.Type> wellTypes = Arrays.asList(
                WellGroup.Type.CONTROL, WellGroup.Type.SPECIMEN,
                WellGroup.Type.REPLICATE, WellGroup.Type.OTHER);

        PlateTypeHandler handler = PlateManager.get().getPlateTypeHandler(template.getType());
        if (handler != null)
            wellTypes = handler.getWellGroupTypes();

        List<String> types = new ArrayList<>();
        for (WellGroup.Type type : wellTypes)
            types.add(type.name());
        return types;
    }

    public void saveChanges(GWTPlate gwtPlate, boolean replaceIfExisting) throws Exception
    {
        try
        {
            PlateTemplate template = PlateService.get().getPlateTemplate(getContainer(), gwtPlate.getName());
            if (template != null)
            {
                if (replaceIfExisting)
                    PlateService.get().deletePlate(getContainer(), template.getRowId());
                else
                    throw new Exception("A plate with name '" + gwtPlate.getName() + "' already exists.");
            }

            template = PlateService.get().createPlateTemplate(getContainer(), gwtPlate.getType(), gwtPlate.getRows(), gwtPlate.getCols());
            template.setName(gwtPlate.getName());
            for (Map.Entry<String, Object> entry : gwtPlate.getPlateProperties().entrySet())
                template.setProperty(entry.getKey(), entry.getValue());

            List<GWTWellGroup> groups = gwtPlate.getGroups();
            for (GWTWellGroup gwtGroup : groups)
            {
                List<Position> positions = new ArrayList<>();
                for (GWTPosition gwtPosition : gwtGroup.getPositions())
                    positions.add(template.getPosition(gwtPosition.getRow(), gwtPosition.getCol()));

                if (!positions.isEmpty())
                {
                    WellGroupTemplate group = template.addWellGroup(gwtGroup.getName(), WellGroup.Type.valueOf(gwtGroup.getType()), positions);

                    for (Map.Entry<String, Object> entry : gwtGroup.getProperties().entrySet())
                        group.setProperty(entry.getKey(), entry.getValue());
                }
            }
            PlateManager.get().getPlateTypeHandler(template.getType()).validate(getContainer(), getUser(), template);
            PlateService.get().save(getContainer(), getUser(), template);
        }
        catch (SQLException | ValidationException e)
        {
            throw new Exception(e);
        }
    }
}
