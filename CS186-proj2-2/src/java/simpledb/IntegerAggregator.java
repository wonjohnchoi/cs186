package simpledb;
import java.util.*;

/**
 * Knows how to compute some aggregate over a set of IntFields.
 */
public class IntegerAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;
    private int gbfield;
    private Type gbfieldtype;
    private int afield;
    private Op what;
    private HashMap<Object, Integer> aggData; // stores aggregated data
    private HashMap<Object, Integer> numTup; // used for AVG

    /**
     * Aggregate constructor
     * 
     * @param gbfield
     *            the 0-based index of the group-by field in the tuple, or
     *            NO_GROUPING if there is no grouping
     * @param gbfieldtype
     *            the type of the group by field (e.g., Type.INT_TYPE), or null
     *            if there is no grouping
     * @param afield
     *            the 0-based index of the aggregate field in the tuple
     * @param what
     *            the aggregation operator
     */

    public IntegerAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        this.gbfield = gbfield;
        this.gbfieldtype = gbfieldtype;
        this.afield = afield;
        this.what = what;
        aggData = new HashMap<Object, Integer>();
        numTup = new HashMap<Object, Integer>();
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the
     * constructor
     * 
     * @param tup
     *            the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
            Object key = null;
            int value = ((IntField)tup.getField(afield)).getValue();
            int aggVal = 0;

            if (gbfield != Aggregator.NO_GROUPING) {
                Field f = tup.getField(gbfield);
                Type fType = f.getType();
                // can we assume that fType is always equal to gbfieldtype?
                if (f.getType() == Type.INT_TYPE) {
                    key = ((IntField)f).getValue();
                } else {
                    key = ((StringField)f).getValue();
                }
            }
            
            switch (what) {
                case SUM: 
                    if (!aggData.containsKey(key)) {
                        aggData.put(key, 0);
                        aggVal = 0;
                    } else {
                        aggVal = aggData.get(key);
                    }    
                    aggVal += value;
                    break;
                case AVG:
                    if (!aggData.containsKey(key)) {
                        aggData.put(key, 0);
                        numTup.put(key, 0);
                        aggVal = 0;
                    } else {
			aggVal = aggData.get(key);
                    }
                    aggVal += value;    
                    int numKey = numTup.get(key);
                    numKey++;
                    numTup.put(key, numKey);
                    break;
                
                case COUNT: 
                    if (!aggData.containsKey(key)) {
                        aggData.put(key, 0);
                        aggVal = 0;
                    } else {
                        aggVal = aggData.get(key);
                    }    
                    aggVal++;
                    break;
                
                case MIN: 
                    if (!aggData.containsKey(key)) {
                        aggData.put(key, Integer.MAX_VALUE);
                        aggVal = Integer.MAX_VALUE;
                    } else {
                        aggVal = aggData.get(key);
                    }    
                    if (aggVal > value) {
                        aggVal = value;
                    }
                    break;
                
                case MAX: 
                    if (!aggData.containsKey(key)) {
                        aggData.put(key, Integer.MIN_VALUE);
                        aggVal = 0;
                    } else {
                        aggVal = aggData.get(key);
                    }    
                    if (aggVal < value) {
                        aggVal = value;
                    }
                    break;
                
            }
            aggData.put(key, aggVal);           
    }

    /**
     * Create a DbIterator over group aggregate results.
     * 
     * @return a DbIterator whose tuples are the pair (groupVal, aggregateVal)
     *         if using group, or a single (aggregateVal) if no grouping. The
     *         aggregateVal is determined by the type of aggregate specified in
     *         the constructor.
     */
    public DbIterator iterator() {
        TupleDesc tupDesc;
        LinkedList<Tuple> tuples = new LinkedList<Tuple>();
        Set keys = aggData.keySet();

        if (gbfield != Aggregator.NO_GROUPING) {
            Type[] types = new Type[2];
            types[0] = this.gbfieldtype;            
            types[1] = Type.INT_TYPE;
                
            String[] names = new String[2];
            names[0] = "groupBy";
            names[1] = what.toString();
            tupDesc = new TupleDesc(types, names);
        } else {
            Type[] types = new Type[1];
            types[0] = Type.INT_TYPE;
            tupDesc = new TupleDesc(types);
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

            if (what == Aggregator.Op.AVG) {
                value = value / numTup.get(key);
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
