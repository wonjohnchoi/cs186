package simpledb;

import java.io.Serializable;
import java.util.*;

/**
 * TupleDesc describes the schema of a tuple.
 */
public class TupleDesc implements Serializable {
    private ArrayList<TDItem> fields;
    private HashMap<String, Integer> fieldNamesToIndex;
    private int tupleDescSize;
    private int hashcode;

    /**
     * A help class to facilitate organizing the information of each field
     * */
    public static class TDItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * The type of the field
         * */
        Type fieldType;
        
        /**
         * The name of the field
         * */
        String fieldName;

        public TDItem(Type t, String n) {
            this.fieldName = n;
            this.fieldType = t;
        }

        public String toString() {
            return fieldName + "(" + fieldType + ")";
        }
    }

    /**
     * @return
     *        An iterator which iterates over all the field TDItems
     *        that are included in this TupleDesc
     * */
    public Iterator<TDItem> iterator() {
        return fields.iterator();
    }

    private static final long serialVersionUID = 1L;

    /**
     * Create a new TupleDesc with typeAr.length fields with fields of the
     * specified types, with associated named fields.
     * 
     * @param typeAr
     *            array specifying the number of and types of fields in this
     *            TupleDesc. It must contain at least one entry.
     * @param fieldAr
     *            array specifying the names of the fields. Note that names may
     *            be null.
     */
    public TupleDesc(Type[] typeAr, String[] fieldAr) {
        fields = new ArrayList<TDItem>(typeAr.length);
        fieldNamesToIndex = new HashMap<String, Integer>();

        for (int i = 0; i < typeAr.length; i++) {
            fields.add(new TDItem(typeAr[i], fieldAr[i]));
            if (!fieldNamesToIndex.containsKey(fieldAr[i])) {
                fieldNamesToIndex.put(fieldAr[i], i);
            }
        }

        // Compute tupleDescSize for getSize method.
        for (int i = 0; i < fields.size(); i++) {
            tupleDescSize += fields.get(i).fieldType.getLen();
        }

        // Compute hashcode for hashcode method.
        // TODO(wonjohn): consider improving hashing method using fieldNames.
        int MOD = Integer.MAX_VALUE;
        int primeNumber = 257;
        // Use long to prevent overflow.
        long multipler = 1;
        long hashcodeTmp = 0;
        for (int i = 0; i < fields.size(); i++) {
            hashcodeTmp = (hashcodeTmp + fields.get(i).fieldType.getLen() * multipler) % MOD;
            multipler = (multipler * primeNumber) % MOD;
        }
        hashcode = (int) hashcodeTmp;
    }

    /**
     * Constructor. Create a new tuple desc with typeAr.length fields with
     * fields of the specified types, with anonymous (unnamed) fields.
     * 
     * @param typeAr
     *            array specifying the number of and types of fields in this
     *            TupleDesc. It must contain at least one entry.
     */
    public TupleDesc(Type[] typeAr) {
        // TODO: anonymous/unamed field?
        this(typeAr, new String[typeAr.length]);
    }

    /**
     * @return the number of fields in this TupleDesc
     */
    public int numFields() {
        return fields.size();
    }

    /**
     * Gets the (possibly null) field name of the ith field of this TupleDesc.
     * 
     * @param i
     *            index of the field name to return. It must be a valid index.
     * @return the name of the ith field
     * @throws NoSuchElementException
     *             if i is not a valid field reference.
     */
    public String getFieldName(int i) throws NoSuchElementException {
        if (i >= fields.size() || i < 0) {
            throw new NoSuchElementException();
        }
        return fields.get(i).fieldName;
    }

    /**
     * Gets the type of the ith field of this TupleDesc.
     * 
     * @param i
     *            The index of the field to get the type of. It must be a valid
     *            index.
     * @return the type of the ith field
     * @throws NoSuchElementException
     *             if i is not a valid field reference.
     */
    public Type getFieldType(int i) throws NoSuchElementException {
        if (i >= fields.size() || i < 0) {
            throw new NoSuchElementException();
        }
        return fields.get(i).fieldType;
    }

    /**
     * Find the index of the field with a given name.
     * 
     * @param name
     *            name of the field.
     * @return the index of the field that is first to have the given name.
     * @throws NoSuchElementException
     *             if no field with a matching name is found.
     */
    public int fieldNameToIndex(String name) throws NoSuchElementException {
        // name == null must throw the exception.
        // See https://piazza.com/class/hhrd9gio9n21s5?cid=67
        if (!fieldNamesToIndex.containsKey(name)) {
            throw new NoSuchElementException();
        }
        return fieldNamesToIndex.get(name);
    }

    /**
     * @return The size (in bytes) of tuples corresponding to this TupleDesc.
     *         Note that tuples from a given TupleDesc are of a fixed size.
     */
    public int getSize() {
        return tupleDescSize;
    }

    /**
     * Merge two TupleDescs into one, with td1.numFields + td2.numFields fields,
     * with the first td1.numFields coming from td1 and the remaining from td2.
     * 
     * @param td1
     *            The TupleDesc with the first fields of the new TupleDesc
     * @param td2
     *            The TupleDesc with the last fields of the TupleDesc
     * @return the new TupleDesc
     */
    public static TupleDesc merge(TupleDesc td1, TupleDesc td2) {
        int newLen = td1.numFields() + td2.numFields();
        Type[] fieldTypes = new Type[newLen];
        String[] fieldNames = new String[newLen];

        for (int i = 0; i < newLen; i++) {
            if (i < td1.numFields()) {
            fieldTypes[i] = td1.getFieldType(i);
            fieldNames[i] = td1.getFieldName(i);
            } else {
            fieldTypes[i] = td2.getFieldType(i - td1.numFields());
            fieldNames[i] = td2.getFieldName(i - td1.numFields());
            }
        }    
        return new TupleDesc(fieldTypes, fieldNames);
    }

    /**
     * Compares the specified object with this TupleDesc for equality. Two
     * TupleDescs are considered equal if they are the same size and if the n-th
     * type in this TupleDesc is equal to the n-th type in td.
     * 
     * @param o
     *            the Object to be compared for equality with this TupleDesc.
     * @return true if the object is equal to this TupleDesc.
     */
    public boolean equals(Object o) {
        if (!(o instanceof TupleDesc)) {
            return false;
        }
        if (numFields() != ((TupleDesc) o).numFields()) {
            return false;
        }
        for (int i = 0; i < numFields(); i++) {
            if (getFieldType(i) != ((TupleDesc) o).getFieldType(i)) {
                return false;
            }
        }
        return true;
    }

    public int hashCode() {
        return hashcode;
    }

    /**
     * Returns a String describing this descriptor. It should be of the form
     * "fieldType[0](fieldName[0]), ..., fieldType[M](fieldName[M])", although
     * the exact format does not matter.
     * 
     * @return String describing this descriptor.
     */
    public String toString() {
        String output = "";
        for (int i = 0; i < fields.size(); i++) {
            output += fields.get(i) + ",";
        }
        return output;
    }
}
