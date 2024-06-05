package org.labkey.specimen.importer;

import java.util.ArrayList;
import java.util.List;

public enum TargetTable
{
    SPECIMEN_EVENTS
    {
        @Override
        public List<String> getTableNames()
        {
            List<String> names = new ArrayList<>(1);
            names.add("SpecimenEvent");
            return names;
        }

        @Override
        public boolean isEvents()
        {
            return true;
        }

        @Override
        public boolean isSpecimens()
        {
            return false;
        }

        @Override
        public boolean isVials()
        {
            return false;
        }
    },
    SPECIMENS
    {
        @Override
        public List<String> getTableNames()
        {
            List<String> names = new ArrayList<>(1);
            names.add("Specimen");
            return names;
        }

        @Override
        public boolean isEvents()
        {
            return false;
        }

        @Override
        public boolean isSpecimens()
        {
            return true;
        }

        @Override
        public boolean isVials()
        {
            return false;
        }
    },
    VIALS
    {
        @Override
        public List<String> getTableNames()
        {
            List<String> names = new ArrayList<>(1);
            names.add("Vial");
            return names;
        }

        @Override
        public boolean isEvents()
        {
            return false;
        }

        @Override
        public boolean isSpecimens()
        {
            return false;
        }

        @Override
        public boolean isVials()
        {
            return true;
        }
    },
    SPECIMENS_AND_SPECIMEN_EVENTS
    {
        @Override
        public List<String> getTableNames()
        {
            List<String> names = new ArrayList<>(1);
            names.add("Specimen");
            names.add("SpecimenEvent");
            return names;
        }

        @Override
        public boolean isEvents()
        {
            return true;
        }

        @Override
        public boolean isSpecimens()
        {
            return true;
        }

        @Override
        public boolean isVials()
        {
            return false;
        }
    },
    VIALS_AND_SPECIMEN_EVENTS
    {
        @Override
        public List<String> getTableNames()
        {
            List<String> names = new ArrayList<>(1);
            names.add("Vial");
            names.add("SpecimenEvent");
            return names;
        }

        @Override
        public boolean isEvents()
        {
            return true;
        }

        @Override
        public boolean isSpecimens()
        {
            return false;
        }

        @Override
        public boolean isVials()
        {
            return true;
        }
    };

    public abstract boolean isEvents();

    public abstract boolean isVials();

    public abstract boolean isSpecimens();

    public abstract List<String> getTableNames();
}
