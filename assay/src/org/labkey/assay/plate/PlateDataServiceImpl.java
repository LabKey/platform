/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.assay.plate;

import gwt.client.org.labkey.plate.designer.client.PlateDataService;
import gwt.client.org.labkey.plate.designer.client.model.GWTPlate;
import gwt.client.org.labkey.plate.designer.client.model.GWTPosition;
import gwt.client.org.labkey.plate.designer.client.model.GWTWellGroup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateLayoutHandler;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.view.ViewContext;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jan 31, 2007
 * Time: 2:38:17 PM
 */
public class PlateDataServiceImpl extends BaseRemoteService implements PlateDataService
{
    private static final Logger LOG = LogManager.getLogger(PlateDataServiceImpl.class);

    public PlateDataServiceImpl(ViewContext context)
    {
        super(context);
    }

    @Override
    public GWTPlate getTemplateDefinition(String templateName, int plateId, String assayTypeName, String templateTypeName, int rowCount, int columnCount, boolean copyTemplate) throws Exception
    {
        try
        {
            Plate template;
            PlateLayoutHandler handler;

            if (templateName != null)
            {
                // existing template
                template = PlateService.get().getPlate(getContainer(), plateId);
                if (template == null)
                    throw new Exception("Plate " + templateName + " does not exist.");

                handler = PlateManager.get().getPlateLayoutHandler(template.getAssayType());
                if (handler == null)
                    throw new Exception("Plate template type " + template.getAssayType() + " does not exist.");
            }
            else
            {
                // new default template
                handler = PlateManager.get().getPlateLayoutHandler(assayTypeName);
                if (handler == null)
                    throw new Exception("Plate template type " + assayTypeName + " does not exist.");

                PlateType plateType = PlateService.get().getPlateType(rowCount, columnCount);
                if (plateType == null)
                    throw new Exception("The plate type : (" + rowCount + " x " + columnCount + ") does not exist");
                template = handler.createPlate(templateTypeName, getContainer(), plateType);
            }

            // Translate PlateTemplate to GWTPlate
            List<? extends WellGroup> groups = template.getWellGroups();
            List<GWTWellGroup> translated = new ArrayList<>();
            for (int i = 0; i < groups.size(); i++)
            {
                WellGroup group = groups.get(i);
                List<GWTPosition> positions = new ArrayList<>(group.getPositions().size());
                for (Position position : group.getPositions())
                    positions.add(new GWTPosition(position.getRow(), position.getColumn()));
                Map<String, Object> groupProperties = new HashMap<>();
                for (String propName : group.getPropertyNames())
                {
                    groupProperties.put(propName, group.getProperty(propName));
                }

                // NOTE: Use negative rowId for unsaved well groups to support GWTWellGroup.equals()
                int wellGroupId = copyTemplate || group.getRowId() == null ? -1 * (i+1) : group.getRowId();
                GWTWellGroup gwtWellGroup = new GWTWellGroup(wellGroupId, group.getType().name(), group.getName(), positions, groupProperties);
                gwtWellGroup.setAllowNewGroups(handler.canCreateNewGroups(group.getType()));
                translated.add(gwtWellGroup);
            }

            int newPlateId = copyTemplate || template.getRowId() == null ? -1 : template.getRowId();
            GWTPlate plate = new GWTPlate(newPlateId,
                    template.getName(), template.getAssayType(), template.getRows(),
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
            LOG.error("Error create plate from template", e);
            throw new Exception(e);
        }
    }

    private List<String> getTypeList(Plate template)
    {
        List<WellGroup.Type> wellTypes = Arrays.asList(
                WellGroup.Type.CONTROL, WellGroup.Type.SPECIMEN,
                WellGroup.Type.REPLICATE, WellGroup.Type.OTHER);

        PlateLayoutHandler handler = PlateManager.get().getPlateLayoutHandler(template.getAssayType());
        if (handler != null)
            wellTypes = handler.getWellGroupTypes();

        List<String> types = new ArrayList<>();
        for (WellGroup.Type type : wellTypes)
            types.add(type.name());
        return types;
    }

    @Override
    public int saveChanges(GWTPlate gwtPlate, boolean replaceIfExisting) throws Exception
    {
        try
        {
            boolean updateExisting = false;
            Plate plate;
            if (gwtPlate.getRowId() > 0)
            {
                plate = PlateManager.get().getPlate(getContainer(), gwtPlate.getRowId());
                if (plate == null)
                    throw new Exception("Plate template not found: " + gwtPlate.getRowId());

                // check another plate of the same name doesn't already exist
                if (PlateManager.get().isDuplicatePlateName(getContainer(), getUser(), gwtPlate.getName(), null) && !replaceIfExisting)
                    throw new Exception("A plate template with name '" + gwtPlate.getName() + "' already exists.");

                if (!plate.getAssayType().equals(gwtPlate.getType()))
                    throw new Exception("Plate template type '" + plate.getAssayType() + "' cannot be changed for '" + gwtPlate.getName() + "'");

                if (plate.getRows() != gwtPlate.getRows() || plate.getColumns() != gwtPlate.getCols())
                    throw new Exception("Plate template dimensions cannot be changed for '" + gwtPlate.getName() + "'");

                // TODO: Use a version column to avoid concurrent updates

                updateExisting = true;
            }
            else
            {
                // check another plate of the same name doesn't already exist
                Plate other = PlateManager.get().getPlateByName(getContainer(), gwtPlate.getName());
                if (other != null)
                {
                    if (!replaceIfExisting)
                        throw new Exception("A plate template with name '" + gwtPlate.getName() + "' already exists.");

                    // delete the existing plate first
                    PlateService.get().deletePlate(getContainer(), getUser(), other.getRowId());
                }

                PlateType plateType = PlateService.get().getPlateType(gwtPlate.getRows(), gwtPlate.getCols());
                if (plateType == null)
                    throw new Exception("The plate type : (" + gwtPlate.getRows() + " x " + gwtPlate.getCols() + ") does not exist");
                plate = PlateManager.get().createPlate(getContainer(), gwtPlate.getType(), plateType);
            }

            plate.setName(gwtPlate.getName());
            plate.setProperties(gwtPlate.getPlateProperties());

            // first, mark well groups not submitted for saving as deleted
            Set<GWTWellGroup> groups = gwtPlate.getGroups();
            List<WellGroup> existingWellGroups = plate.getWellGroups();
            for (WellGroup existingWellGroup : existingWellGroups)
            {
                if (groups.stream().noneMatch(g-> g.getRowId() == existingWellGroup.getRowId()))
                    ((PlateImpl)plate).markWellGroupForDeletion(existingWellGroup);
            }

            // next, update positions on existing well groups or create new well groups
            for (GWTWellGroup gwtGroup : groups)
            {
                WellGroup.Type groupType = WellGroup.Type.valueOf(gwtGroup.getType());
                List<Position> positions = new ArrayList<>();
                for (GWTPosition gwtPosition : gwtGroup.getPositions())
                    positions.add(plate.getPosition(gwtPosition.getRow(), gwtPosition.getCol()));

                WellGroupImpl group;
                if (updateExisting && gwtGroup.getRowId() > 0)
                {
                    group = findExistingWellGroup(existingWellGroups, gwtGroup.getRowId());
                    if (group == null)
                        throw new Exception("Well group " + gwtGroup.getRowId() + " wasn't found");
                    if (group.getType() != groupType)
                        throw new Exception("Well group cannot be changed: " + gwtGroup.getName());

                    group.setName(gwtGroup.getName());
                    group.setPositions(positions);

                    ((PlateImpl)plate).storeWellGroup(group);
                }
                else
                {
                    assert gwtGroup.getRowId() <= 0 : "Updating existing well group on a new template";
                    group = (WellGroupImpl) plate.addWellGroup(gwtGroup.getName(), groupType, positions);
                }

                group.setProperties(gwtGroup.getProperties());
            }

            PlateManager.get().getPlateLayoutHandler(plate.getAssayType()).validatePlate(getContainer(), getUser(), plate);
            return PlateService.get().save(getContainer(), getUser(), plate);
        }
        catch (BatchValidationException | ValidationException e)
        {
            LOG.error("Error saving plate", e);
            throw new Exception(e);
        }
    }

    private WellGroupImpl findExistingWellGroup(List<WellGroup> wellGroups, int rowId)
    {
        for (WellGroup wellGroup : wellGroups)
        {
            if (wellGroup.getRowId() != null && wellGroup.getRowId() == rowId)
                return (WellGroupImpl) wellGroup;
        }
        return null;
    }
}
