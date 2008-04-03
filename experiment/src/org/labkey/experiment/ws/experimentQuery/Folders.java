/**
 * Folders.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Jul 07, 2006 (02:38:00 PDT) WSDL2Java emitter.
 */

package org.labkey.experiment.ws.experimentQuery;

public class Folders  implements java.io.Serializable {
    private org.labkey.experiment.ws.experimentQuery.Folder[] folder;

    public Folders() {
    }

    public Folders(
           org.labkey.experiment.ws.experimentQuery.Folder[] folder) {
           this.folder = folder;
    }


    /**
     * Gets the folder value for this Folders.
     * 
     * @return folder
     */
    public org.labkey.experiment.ws.experimentQuery.Folder[] getFolder() {
        return folder;
    }


    /**
     * Sets the folder value for this Folders.
     * 
     * @param folder
     */
    public void setFolder(org.labkey.experiment.ws.experimentQuery.Folder[] folder) {
        this.folder = folder;
    }

    public org.labkey.experiment.ws.experimentQuery.Folder getFolder(int i) {
        return this.folder[i];
    }

    public void setFolder(int i, org.labkey.experiment.ws.experimentQuery.Folder _value) {
        this.folder[i] = _value;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof Folders)) return false;
        Folders other = (Folders) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.folder==null && other.getFolder()==null) || 
             (this.folder!=null &&
              java.util.Arrays.equals(this.folder, other.getFolder())));
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
        if (getFolder() != null) {
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getFolder());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getFolder(), i);
                if (obj != null &&
                    !obj.getClass().isArray()) {
                    _hashCode += obj.hashCode();
                }
            }
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(Folders.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://cpas.fhcrc.org/experimentQuery/xml", "Folders"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("folder");
        elemField.setXmlName(new javax.xml.namespace.QName("http://cpas.fhcrc.org/experimentQuery/xml", "Folder"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://cpas.fhcrc.org/experimentQuery/xml", "Folder"));
        elemField.setNillable(false);
        elemField.setMaxOccursUnbounded(true);
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
