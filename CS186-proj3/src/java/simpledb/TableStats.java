package simpledb;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TableStats represents statistics (e.g., histograms) about base tables in a
 * query. 
 * 
 * This class is not needed in implementing proj1 and proj2.
 */
public class TableStats {

    private static final ConcurrentHashMap<String, TableStats> statsMap = new ConcurrentHashMap<String, TableStats>();

    static final int IOCOSTPERPAGE = 1000;

    public static TableStats getTableStats(String tablename) {
        return statsMap.get(tablename);
    }

    public static void setTableStats(String tablename, TableStats stats) {
        statsMap.put(tablename, stats);
    }
    
    public static void setStatsMap(HashMap<String,TableStats> s)
    {
        try {
            java.lang.reflect.Field statsMapF = TableStats.class.getDeclaredField("statsMap");
            statsMapF.setAccessible(true);
            statsMapF.set(null, s);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

    }

    public static Map<String, TableStats> getStatsMap() {
        return statsMap;
    }

    public static void computeStatistics() {
        Iterator<Integer> tableIt = Database.getCatalog().tableIdIterator();

        System.out.println("Computing table stats.");
        while (tableIt.hasNext()) {
            int tableid = tableIt.next();
            TableStats s = new TableStats(tableid, IOCOSTPERPAGE);
            setTableStats(Database.getCatalog().getTableName(tableid), s);
        }
        System.out.println("Done.");
    }

    /**
     * Number of bins for the histogram. Feel free to increase this value over
     * 100, though our tests assume that you have at least 100 bins in your
     * histograms.
     */
    static final int NUM_HIST_BINS = 100;
    private IntHistogram intHist;
    private StringHistogram strHist;
    private int ioCostPerPage;
    private int numPages;
    private int numTuples;

    /**
     * Create a new TableStats object, that keeps track of statistics on each
     * column of a table
     * 
     * @param tableid
     *            The table over which to compute statistics
     * @param ioCostPerPage
     *            The cost per page of IO. This doesn't differentiate between
     *            sequential-scan IO and disk seeks.
     */
    public TableStats(int tableid, int ioCostPerPage) {
        DbFile df = Database.getCatalog().getDbFile(tableid);
        DbFileIterator dfIt = df.iterator(null);
        IntHistogram intHist = null;
        StringHistogram strHist = null;

        numTuples = 0;
        try {
            // pass 1
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            dfIt.open();
            while (dfIt.hasNext()) {
                Tuple tup = dfIt.next();
                ++numTuples;
                Iterator<Field> fieldIt = tup.fields();
                while (fieldIt.hasNext()) {
                    Field field = fieldIt.next();
                    Type type = field.getType();
                    if (type == Type.INT_TYPE) {
                        int val = ((IntField) field).getValue();
                        max = Math.max(max, val);
                        min = Math.min(min, val);
                    }
                }
            }
            dfIt.close();

            // pass 2
            dfIt.open();
            intHist = new IntHistogram(1000, min, max);
            strHist = new StringHistogram(1000);
            while (dfIt.hasNext()) {
                Tuple tup = dfIt.next();
                Iterator<Field> fieldIt = tup.fields();
                while (fieldIt.hasNext()) {
                    Field field = fieldIt.next();
                    Type type = field.getType();
                    if (type == Type.INT_TYPE) {
                        intHist.addValue(((IntField) field).getValue());
                    } else if (type == Type.STRING_TYPE) {
                        strHist.addValue(((StringField) field).getValue());
                    } else {
                        System.out.println("Unknown Field Type: " + type);
                        System.exit(1);
                    }
                }
            }
        } catch (DbException ex) {
            ex.printStackTrace();
        } catch (TransactionAbortedException ex) {
            ex.printStackTrace();
        }
        this.intHist = intHist;
        this.strHist = strHist;
        this.ioCostPerPage = ioCostPerPage;
        this.numPages = ((HeapFile) df).numPages();
    }

    /**
     * Estimates the cost of sequentially scanning the file, given that the cost
     * to read a page is costPerPageIO. You can assume that there are no seeks
     * and that no pages are in the buffer pool.
     * 
     * Also, assume that your hard drive can only read entire pages at once, so
     * if the last page of the table only has one tuple on it, it's just as
     * expensive to read as a full page. (Most real hard drives can't
     * efficiently address regions smaller than a page at a time.)
     * 
     * @return The estimated cost of scanning the table.
     */
    public double estimateScanCost() {
        return ioCostPerPage * numPages;
    }

    /**
     * This method returns the number of tuples in the relation, given that a
     * predicate with selectivity selectivityFactor is applied.
     * 
     * @param selectivityFactor
     *            The selectivity of any predicates over the table
     * @return The estimated cardinality of the scan with the specified
     *         selectivityFactor
     */
    public int estimateTableCardinality(double selectivityFactor) {
        return (int) (numTuples * selectivityFactor);
    }

    /**
     * The average selectivity of the field under op.
     * @param field
     *        the index of the field
     * @param op
     *        the operator in the predicate
     * The semantic of the method is that, given the table, and then given a
     * tuple, of which we do not know the value of the field, return the
     * expected selectivity. You may estimate this value from the histograms.
     * */
    public double avgSelectivity(int field, Predicate.Op op) {
        // some code goes here
        return 1.0;
    }

    /**
     * Estimate the selectivity of predicate <tt>field op constant</tt> on the
     * table.
     * 
     * @param field
     *            The field over which the predicate ranges
     * @param op
     *            The logical operation in the predicate
     * @param constant
     *            The value against which the field is compared
     * @return The estimated selectivity (fraction of tuples that satisfy) the
     *         predicate
     */
    public double estimateSelectivity(int field, Predicate.Op op, Field constant) {
        Type type = constant.getType();
        double selectivity = -1;
        if (type == Type.INT_TYPE) {
            selectivity = intHist.estimateSelectivity(op, ((IntField) constant).getValue());
        } else if (type == Type.STRING_TYPE) {
            selectivity = strHist.estimateSelectivity(op, ((StringField) constant).getValue());
        } else {
            System.out.println("Unknown Type: " + type);
            System.exit(1);
        }
        return selectivity;
    }

    /**
     * return the total number of tuples in this table
     * */
    public int totalTuples() {
        // some code goes here
        return 0;
    }

}
