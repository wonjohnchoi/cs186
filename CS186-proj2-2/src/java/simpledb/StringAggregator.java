package simpledb;
import java.util.*;
/**
 * Knows how to compute some aggregate over a set of StringFields.
 */
public class StringAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;
    private int gbfield;
    private Type gbfieldtype;
    private int afield;
    private Op what;
    private HashMap<Integer, Integer> aggData;
    private String gbfieldName;
    private String afieldName;

    /**
     * Aggregate constructor
     * @param gbfield the 0-based index of the group-by field in the tuple, or NO_GROUPING if there is no grouping
     * @param gbfieldtype the type of the group by field (e.g., Type.INT_TYPE), or null if there is no grouping
     * @param afield the 0-based index of the aggregate field in the tuple
     * @param what aggregation operator to use -- only supports COUNT
     * @throws IllegalArgumentException if what != COUNT
     */

    public StringAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        this.gbfield = gbfield;
        this.gbfieldtype = gbfieldtype;
        this.afield = afield;
        this.what = what;
        aggData = new HashMap<Integer, Integer>();
        gbfieldName = "";
        afieldName = "";
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the constructor
     * @param tup the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        // COUNT only
        IntField f = new IntField(Aggregator.NO_GROUPING);
        int key;
        if (gbfield != Aggregator.NO_GROUPING) {
            f = (IntField) tup.getField(gbfield);
            gbfieldName = tup.getTupleDesc().getFieldName(gbfield);
        }
        key = f.getValue();
        afieldName = tup.getTupleDesc().getFieldName(afield);
        if (!aggData.containsKey(key)) {
                aggData.put(key, 0);
        }
        int aggVal = aggData.get(key);
        aggVal++;
        aggData.put(key, aggVal);
    }

    /**
     * Create a DbIterator over group aggregate results.
     *
     * @return a DbIterator whose tuples are the pair (groupVal,
     *   aggregateVal) if using group, or a single (aggregateVal) if no
     *   grouping. The aggregateVal is determined by the type of
     *   aggregate specified in the constructor.
     */
    public DbIterator iterator() {
        TupleDesc tupDesc;
        LinkedList<Tuple> tuples = new LinkedList<Tuple>();
        Set keys = aggData.keySet();

        if (gbfield != Aggregator.NO_GROUPING) {
            Type[] types = new Type[2];
            String[] names = new String[2];
            types[0] = gbfieldtype;            
            types[1] = Type.INT_TYPE;                
            names[0] = gbfieldName;
            names[1] = afieldName;
            tupDesc = new TupleDesc(types, names);

        } else {
            Type[] type = new Type[1];
            String[] name = new String[1];
            type[0] = Type.INT_TYPE;
            name[0] = afieldName;
            tupDesc = new TupleDesc(type, name);
        }
        // adding tuples
        for (Object key: keys) {
            int value = aggData.get(key);
            Tuple tuple = new Tuple(tupDesc);
            Field groupBy = new IntField(0);
            if (gbfield != Aggregator.NO_GROUPING) {
                if (gbfieldtype == Type.INT_TYPE) {
                    groupBy = new IntField((Integer) key);
                } else if (gbfieldtype == Type.STRING_TYPE) {
                    groupBy = new StringField((String) key, gbfieldtype.getLen());
                }
            }
            Field aggValue = new IntField(value);
            if (gbfield != Aggregator.NO_GROUPING) {
                tuple.setField(0, groupBy);
                tuple.setField(1, aggValue);
            } else {
                tuple.setField(0, aggValue);
            }  
            tuples.add(tuple);
        }
        return new TupleIterator(tupDesc, tuples);    
    }

}
