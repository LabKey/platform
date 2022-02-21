package org.labkey.specimen.actions;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.Container;
import org.labkey.api.data.MenuButton;
import org.labkey.api.specimen.SpecimenRequestStatus;
import org.labkey.api.specimen.Vial;
import org.labkey.api.specimen.location.LocationImpl;
import org.labkey.api.specimen.location.LocationManager;
import org.labkey.api.specimen.model.SpecimenRequestActor;
import org.labkey.api.specimen.requirements.SpecimenRequest;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirement;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirementProvider;
import org.labkey.api.study.Location;
import org.labkey.api.util.Button;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DisplayElement;
import org.labkey.api.view.ViewContext;
import org.labkey.specimen.SpecimenRequestManager;
import org.labkey.specimen.actions.SpecimenController.ImportVialIdsAction;
import org.labkey.specimen.actions.SpecimenController.OverviewAction;
import org.labkey.specimen.actions.SpecimenController.RemoveRequestSpecimensAction;
import org.labkey.specimen.notifications.ActorNotificationRecipientSet;
import org.labkey.specimen.security.permissions.ManageRequestsPermission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ManageRequestBean extends SpecimensViewBean
{
    protected SpecimenRequest _specimenRequest;
    protected boolean _requestManager;
    protected boolean _requirementsComplete;
    protected boolean _finalState;
    private List<ActorNotificationRecipientSet> _possibleNotifications;
    protected List<String> _missingSpecimens;
    private final Boolean _submissionResult;
    private Location[] _providingLocations;
    private String _returnUrl;
    private final Container _container;

    public ManageRequestBean(ViewContext context, SpecimenRequest specimenRequest, boolean forExport, Boolean submissionResult, String returnUrl)
    {
        super(context, specimenRequest.getVials(), !forExport, !forExport, forExport, false);
        _container = context.getContainer();
        _submissionResult = submissionResult;
        _requestManager = context.getContainer().hasPermission(context.getUser(), ManageRequestsPermission.class);
        _specimenRequest = specimenRequest;
        _finalState = SpecimenRequestManager.get().isInFinalState(_specimenRequest);
        _requirementsComplete = true;
        _missingSpecimens = SpecimenRequestManager.get().getMissingSpecimens(_specimenRequest);
        _returnUrl = returnUrl;
        SpecimenRequestRequirement[] requirements = specimenRequest.getRequirements();
        for (int i = 0; i < requirements.length && _requirementsComplete; i++)
        {
            SpecimenRequestRequirement requirement = requirements[i];
            _requirementsComplete = requirement.isComplete();
        }

        if (_specimenQueryView != null)
        {
            List<DisplayElement> buttons = new ArrayList<>();

            MenuButton exportMenuButton = new MenuButton("Export");
            ActionURL exportExcelURL = context.getActionURL().clone().addParameter("export", "excel");
            ActionURL exportTextURL = context.getActionURL().clone().addParameter("export", "tsv");
            exportMenuButton.addMenuItem("Export all to Excel (.xls)", exportExcelURL);
            exportMenuButton.addMenuItem("Export all to text file (.tsv)", exportTextURL);
            buttons.add(exportMenuButton);
            _specimenQueryView.setShowExportButtons(false);
            _specimenQueryView.getSettings().setAllowChooseView(false);

            if (SpecimenRequestManager.get().hasEditRequestPermissions(context.getUser(), specimenRequest) && !_finalState)
            {
                ActionButton addButton = new ActionButton(new ActionURL(OverviewAction.class, _container), "Specimen Search");
                ActionButton deleteButton = new ActionButton(RemoveRequestSpecimensAction.class, "Remove Selected");
                _specimenQueryView.addHiddenFormField("id", "" + specimenRequest.getRowId());
                buttons.add(addButton);

                ActionURL importActionURL = new ActionURL(ImportVialIdsAction.class, _container);
                importActionURL.addParameter("id", specimenRequest.getRowId());
                Button importButton = new Button.ButtonBuilder("Upload Specimen Ids")
                        .href(importActionURL)
                        .submit(false)
                        .build();
                buttons.add(importButton);
                buttons.add(deleteButton);
            }
            _specimenQueryView.setButtons(buttons);
        }
    }

    public List<ActorNotificationRecipientSet> getPossibleNotifications(SpecimenRequest specimenRequest)
    {
        List<ActorNotificationRecipientSet> possibleNotifications = new ArrayList<>();
        // allow notification of all parties listed in the request requirements:
        for (SpecimenRequestRequirement requirement : specimenRequest.getRequirements())
            addIfNotPresent(requirement.getActor(), requirement.getLocation(), possibleNotifications);

        // allow notification of all site-based actors at the destination site, and all study-wide actors:
        Map<Integer, LocationImpl> relevantSites = new HashMap<>();
        if (specimenRequest.getDestinationSiteId() == null)
        {
            throw new IllegalStateException("Request " + specimenRequest.getRowId() + " in folder " +
                    specimenRequest.getContainer().getPath() + " does not have a valid destination site id.");
        }
        LocationImpl destLocation = LocationManager.get().getLocation(specimenRequest.getContainer(), specimenRequest.getDestinationSiteId().intValue());
        relevantSites.put(destLocation.getRowId(), destLocation);
        for (Vial vial : specimenRequest.getVials())
        {
            LocationImpl location = LocationManager.get().getCurrentLocation(vial);
            if (location != null && !relevantSites.containsKey(location.getRowId()))
                relevantSites.put(location.getRowId(), location);
        }

        SpecimenRequestActor[] allActors = SpecimenRequestRequirementProvider.get().getActors(specimenRequest.getContainer());
        // add study-wide actors and actors from all relevant sites:
        for (SpecimenRequestActor actor : allActors)
        {
            if (actor.isPerSite())
            {
                for (LocationImpl location : relevantSites.values())
                {
                    if (actor.isPerSite())
                        addIfNotPresent(actor, location, possibleNotifications);
                }
            }
            else
                addIfNotPresent(actor, null, possibleNotifications);
        }

        possibleNotifications.sort((first, second) ->
        {
            String firstSite = first.getLocation() != null ? first.getLocation().getLabel() : "";
            String secondSite = second.getLocation() != null ? second.getLocation().getLabel() : "";
            int comp = firstSite.compareToIgnoreCase(secondSite);
            if (comp == 0)
            {
                String firstActorLabel = first.getActor().getLabel();
                if (firstActorLabel == null)
                    firstActorLabel = "";
                String secondActorLabel = second.getActor().getLabel();
                if (secondActorLabel == null)
                    secondActorLabel = "";
                comp = firstActorLabel.compareToIgnoreCase(secondActorLabel);
            }
            return comp;
        });
        return possibleNotifications;
    }

    public synchronized List<ActorNotificationRecipientSet> getPossibleNotifications()
    {
        if (_possibleNotifications == null)
            _possibleNotifications = getPossibleNotifications(_specimenRequest);
        return _possibleNotifications;
    }

    public SpecimenRequest getSpecimenRequest()
    {
        return _specimenRequest;
    }

    public boolean hasMissingSpecimens()
    {
        return _missingSpecimens != null && !_missingSpecimens.isEmpty();
    }

    public List<String> getMissingSpecimens()
    {
        return _missingSpecimens;
    }

    public SpecimenRequestStatus getStatus()
    {
        return SpecimenRequestManager.get().getRequestStatus(_specimenRequest.getContainer(), _specimenRequest.getStatusId());
    }

    public Location getDestinationSite()
    {
        Integer destinationSiteId = _specimenRequest.getDestinationSiteId();
        if (destinationSiteId != null)
        {
            return LocationManager.get().getLocation(_specimenRequest.getContainer(), destinationSiteId.intValue());
        }
        return null;
    }

    public boolean isRequestManager()
    {
        return _requestManager;
    }

    public boolean isFinalState()
    {
        return _finalState;
    }

    public boolean isRequirementsComplete()
    {
        return _requirementsComplete;
    }

    public Boolean isSuccessfulSubmission()
    {
        return _submissionResult != null && _submissionResult.booleanValue();
    }

    public Location[] getProvidingLocations()
    {
        if (_providingLocations == null)
        {
            Set<Integer> locationSet = new HashSet<>();
            for (Vial vial : _vials)
            {
                Integer locationId = vial.getCurrentLocation();
                if (locationId != null)
                    locationSet.add(locationId);
            }
            _providingLocations = new Location[locationSet.size()];
            int i = 0;
            for (Integer locationId : locationSet)
                _providingLocations[i++] = LocationManager.get().getLocation(_container, locationId);
        }
        return _providingLocations;
    }

    public String getReturnUrl()
    {
        return _returnUrl;
    }

    public void setReturnUrl(String returnUrl)
    {
        _returnUrl = returnUrl;
    }

    private boolean addIfNotPresent(SpecimenRequestActor actor, LocationImpl location, List<ActorNotificationRecipientSet> list)
    {
        for (ActorNotificationRecipientSet actorSite : list)
        {
            if (actorSite.getActor().getRowId() == actor.getRowId())
            {
                if (actorSite.getLocation() == null && location == null)
                    return false;
                else
                if (actorSite.getLocation() != null && location != null && actorSite.getLocation().getRowId() == location.getRowId())
                    return false;
            }
        }
        list.add(new ActorNotificationRecipientSet(actor, location));
        return true;
    }
}
