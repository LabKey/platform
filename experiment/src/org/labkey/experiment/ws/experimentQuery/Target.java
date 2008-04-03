/**
 * Target.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Jul 07, 2006 (02:38:00 PDT) WSDL2Java emitter.
 */

package org.labkey.experiment.ws.experimentQuery;


/**
 * This is the query target.
 */
public class Target  implements java.io.Serializable {
    private org.labkey.experiment.ws.experimentQuery.Group[] group;

    private org.labkey.experiment.ws.experimentQuery.Objects objects;

    private org.labkey.experiment.ws.experimentQuery.Property[] property;

    private float APIVersion;

    private java.lang.String name;  // attribute

    public Target() {
    }

    public Target(
           org.labkey.experiment.ws.experimentQuery.Group[] group,
           org.labkey.experiment.ws.experimentQuery.Objects objects,
           org.labkey.experiment.ws.experimentQuery.Property[] property,
           float APIVersion,
           java.lang.String name) {
           this.group = group;
           this.objects = objects;
           this.property = property;
           this.APIVersion = APIVersion;
           this.name = name;
    }


    /**
     * Gets the group value for this Target.
     * 
     * @return group
     */
    public org.labkey.experiment.ws.experimentQuery.Group[] getGroup() {
        return group;
    }


    /**
     * Sets the group value for this Target.
     * 
     * @param group
     */
    public void setGroup(org.labkey.experiment.ws.experimentQuery.Group[] group) {
        this.group = group;
    }

    public org.labkey.experiment.ws.experimentQuery.Group getGroup(int i) {
        return this.group[i];
    }

    public void setGroup(int i, org.labkey.experiment.ws.experimentQuery.Group _value) {
        this.group[i] = _value;
    }


    /**
     * Gets the objects value for this Target.
     * 
     * @return objects
     */
    public org.labkey.experiment.ws.experimentQuery.Objects getObjects() {
        return objects;
    }


    /**
     * Sets the objects value for this Target.
     * 
     * @param objects
     */
    public void setObjects(org.labkey.experiment.ws.experimentQuery.Objects objects) {
        this.objects = objects;
    }


    /**
     * Gets the property value for this Target.
     * 
     * @return property
     */
    public org.labkey.experiment.ws.experimentQuery.Property[] getProperty() {
        return property;
    }


    /**
     * Sets the property value for this Target.
     * 
     * @param property
     */
    public void setProperty(org.labkey.experiment.ws.experimentQuery.Property[] property) {
        this.property = property;
    }

    public org.labkey.experiment.ws.experimentQuery.Property getProperty(int i) {
        return this.property[i];
    }

    public void setProperty(int i, org.labkey.experiment.ws.experimentQuery.Property _value) {
        this.property[i] = _value;
    }


    /**
     * Gets the APIVersion value for this Target.
     * 
     * @return APIVersion
     */
    public float getAPIVersion() {
        return APIVersion;
    }


    /**
     * Sets the APIVersion value for this Target.
     * 
     * @param APIVersion
     */
    public void setAPIVersion(float APIVersion) {
        this.APIVersion = APIVersion;
    }


    /**
     * Gets the name value for this Target.
     * 
     * @return name
     */
    public java.lang.String getName() {
        return name;
    }


    /**
     * Sets the name value for this Target.
     * 
     * @param name
     */
    public void setName(java.lang.String name) {
        this.name = name;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof Target)) return false;
        Target other = (Target) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.group==null && other.getGroup()==null) || 
             (this.group!=null &&
              java.util.Arrays.equals(this.group, other.getGroup()))) &&
            ((this.objects==null && other.getObjects()==null) || 
             (this.objects!=null &&
              this.objects.equals(other.getObjects()))) &&
            ((this.property==null && other.getProperty()==null) || 
             (this.property!=null &&
              java.util.Arrays.equals(this.property, other.getProperty()))) &&
            this.APIVersion == other.getAPIVersion() &&
            ((this.name==null && other.getName()==null) || 
             (this.name!=null &&
              this.name.equals(other.getName())));
        __equalsCalc = null;
        return _equals;
    }

    private boolean __hashCodeCalc = false;
    public synchronized int hashCode() {
        if (__hashCodeCalc) {
            return 0;
        }
        __hashCodeCalc = true;
        int _hashCode = 1;
        if (getGroup() != null) {
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getGroup());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getGroup(), i);
                if (obj != null &&
                    !obj.getClass().isArray()) {
                    _hashCode += obj.hashCode();
                }
            }
        }
        if (getObjects() != null) {
            _hashCode += getObjects().hashCode();
        }
        if (getProperty() != null) {
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getProperty());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getProperty(), i);
                if (obj != null &&
                    !obj.getClass().isArray()) {
                    _hashCode += obj.hashCode();
                }
            }
        }
        _hashCode += new Float(getAPIVersion()).hashCode();
        if (getName() != null) {
            _hashCode += getName().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(Target.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://cpas.fhcrc.org/experimentQuery/xml", "Target"));
        org.apache.axis.description.AttributeDesc attrField = new org.apache.axis.description.AttributeDesc();
        attrField.setFieldName("name");
        attrField.setXmlName(new javax.xml.namespace.QName("", "name"));
        attrField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        typeDesc.addFieldDesc(attrField);
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("group");
        elemField.setXmlName(new javax.xml.namespace.QName("http://cpas.fhcrc.org/experimentQuery/xml", "Group"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://cpas.fhcrc.org/experimentQuery/xml", "Group"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        elemField.setMaxOccursUnbounded(true);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("objects");
        elemField.setXmlName(new javax.xml.namespace.QName("http://cpas.fhcrc.org/experimentQuery/xml", "Objects"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://cpas.fhcrc.org/experimentQuery/xml", "Objects"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("property");
        elemField.setXmlName(new javax.xml.namespace.QName("http://cpas.fhcrc.org/experimentQuery/xml", "Property"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://cpas.fhcrc.org/experimentQuery/xml", "Property"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        elemField.setMaxOccursUnbounded(true);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("APIVersion");
        elemField.setXmlName(new javax.xml.namespace.QName("http://cpas.fhcrc.org/experimentQuery/xml", "APIVersion"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "float"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
    }

    /**
     * Return type metadata object
     */
    public static org.apache.axis.description.TypeDesc getTypeDesc() {
        return typeDesc;
    }

    /**
     * Get Custom Serializer
     */
    public static org.apache.axis.encoding.Serializer getSerializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.BeanSerializer(
            _javaType, _xmlType, typeDesc);
    }

    /**
     * Get Custom Deserializer
     */
    public static org.apache.axis.encoding.Deserializer getDeserializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.BeanDeserializer(
            _javaType, _xmlType, typeDesc);
    }

}
