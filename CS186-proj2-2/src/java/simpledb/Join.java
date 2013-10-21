package simpledb;

import java.util.*;

/**
 * The Join operator implements the relational join operation.
 */
public class Join extends Operator {

    private static final long serialVersionUID = 1L;

    private JoinPredicate p;
    private DbIterator child1;
    private DbIterator child2;
    private DbIterator childIt1;
    private DbIterator childIt2;

    private List<Tuple> index2;
    private List<Tuple> page1;
    private List<Tuple> page2;
    private int i1;
    private int i2;
    private int numTuples1, numTuples2;
    private boolean fit1 = true;
    private boolean fit2 = true;
    private boolean switched = false;
    /**
     * Constructor. Accepts to children to join and the predicate to join them
     * on
     * 
     * @param p
     *            The predicate to use to join the children
     * @param child1
     *            Iterator for the left(outer) relation to join
     * @param child2
     *            Iterator for the right(inner) relation to join
     */
    public Join(JoinPredicate p, DbIterator child1, DbIterator child2) {
        int tupleSize1 = child1.getTupleDesc().getSize();
        int numTuples1 = (int)((BufferPool.PAGE_SIZE * 8) / (tupleSize1 * 8 + 1));
        int tupleSize2 = child1.getTupleDesc().getSize();
        int numTuples2 = (int)((BufferPool.PAGE_SIZE * 8) / (tupleSize2 * 8 + 1));

        this.numTuples1 = numTuples1;
        this.numTuples2 = numTuples2;
        System.out.println("tupleNum1: " + numTuples1);
        System.out.println("tupleNum2: " + numTuples2);
        System.out.println("child1: "+child1.getTupleDesc());
        System.out.println("child2: "+child2.getTupleDesc());
        System.out.println("idx1: "+p.getField1());
        System.out.println("idx2: "+p.getField2());
        this.p = p;
        this.child1 = child1;
        this.child2 = child2;
        page1 = new ArrayList<Tuple>();
        page2 = new ArrayList<Tuple>();
        index2 = new ArrayList<Tuple>();
        try {
            int i = 0;
            child1.open();
            while (child1.hasNext()) {
                if (i >= numTuples1) {
                    fit1 = false;
                    break;
                }
                page1.add(child1.next());
                i += 1;
                if (i % 10000 == 0) {
                    System.out.println(i);
                }
            }
            System.out.println("Size of child1: " + i);
            child1.close();

            child2.open();
            i = 0;
            while (child2.hasNext()) {
                if (i >= numTuples2) {
                    fit2 = false;
                    break;
                }

                i += 1;
                // index2.add(child2.next());
                page2.add(child2.next());
                if (i % 10000 == 0) {
                    System.out.println(i);
                }
                if (i >= numTuples2) {
                    fit2 = false;
                    break;
                }
            }
            System.out.println("Size of index: " + i);
            child2.close();
        } catch (Exception ex) { /* fine to use Exception because this should never happen. */
            ex.printStackTrace();
            System.exit(1);
        }
        i2 = 0;
        i1 = 0;

        childIt1 = child1;
        childIt2 = child2;
        if (fit1) {
            System.out.println("Fit1 Perfect!");
            page2.clear();
            switched = false;
        } else if(fit2) {
            System.out.println("Fit2 Switching..");
            List<Tuple> tmp;
            tmp = page1;
            page1 = page2;
            page2 = tmp;
            page2.clear();

            childIt2 = child1;
            childIt1 = child2;
            switched = true;
        } else {
            System.out.println("Neither fits..");
            page1.clear();
            page2.clear();
        }
    }

    public JoinPredicate getJoinPredicate() {
        return p;
    }

    /**
     * @return
     *       the field name of join field1. Should be quantified by
     *       alias or table name.
     * */
    public String getJoinField1Name() {
        return child1.getTupleDesc().getFieldName(p.getField1());
    }

    /**
     * @return
     *       the field name of join field2. Should be quantified by
     *       alias or table name.
     * */
    public String getJoinField2Name() {
        // some code goes here
        return child2.getTupleDesc().getFieldName(p.getField2());
    }

    /**
     * @see simpledb.TupleDesc#merge(TupleDesc, TupleDesc) for possible
     *      implementation logic.
     */
    public TupleDesc getTupleDesc() {
        return TupleDesc.merge(child1.getTupleDesc(), child2.getTupleDesc());
    }

    public void open() throws DbException, NoSuchElementException,
            TransactionAbortedException {
        super.open();
        childIt1.open();
        childIt2.open();
    }

    public void close() {
        super.close();
        childIt1.close();
        childIt2.close();
    }

    public void rewind() throws DbException, TransactionAbortedException {
        childIt1.rewind();
        childIt2.rewind();
        next1 = null;
    }


    private Tuple next1 = null;
    /*
    private Tuple getNext1() {
        if (page1 == null || i1 == page1.size()) {
            i1 = 0;
            page1 = new ArrayList<Tuple>();
            for (int i = 0; i < numTuples1 && child1.hasNext(); i++) {
                page1.add(child1.next());
            }
        }
        if (page1.size() == 0) {
            return null;
        }
        return page1.get(i1++);
    }

    private Tuple getNext2() {
        if (page2 == null || i2 == page2.size()) {
            i2 = 0;
            page2 = new ArrayList<Tuple>();
            for (int i = 0; i < numTuples2 && child2.hasNext(); i++) {
                page2.add(child2.next());
            }
        }
        if (page2.size() == 0) {
            return null;
        }
        return page2.get(i2++);
        }*/

    private Tuple combineTuples(Tuple next1, Tuple next2)  throws TransactionAbortedException, DbException {
        Tuple next = new Tuple(getTupleDesc());
        int i = 0;
        for (Iterator<Field> fields = next1.fields(); fields.hasNext();) {
            next.setField(i++, fields.next());
        }
        for (Iterator<Field> fields = next2.fields(); fields.hasNext();) {
                    next.setField(i++, fields.next());
        }
        return next;
    }

    /**
     * Returns the next tuple generated by the join, or null if there are no
     * more tuples. Logically, this is the next tuple in r1 cross r2 that
     * satisfies the join predicate. There are many possible implementations;
     * the simplest is a nested loops join.
     * <p>
     * Note that the tuples returned from this particular implementation of Join
     * are simply the concatenation of joining tuples from the left and right
     * relation. Therefore, if an equality predicate is used there will be two
     * copies of the join attribute in the results. (Removing such duplicate
     * columns can be done with an additional projection operator if needed.)
     * <p>
     * For example, if one tuple is {1,2,3} and the other tuple is {1,5,6},
     * joined on equality of the first column, then this returns {1,2,3,1,5,6}.
     * 
     * @return The next matching tuple.
     * @see JoinPredicate#filter
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        //System.out.println("A");
        if (fit1 || fit2) {
            //System.out.println("B");
            Tuple next2 = null;
            while (true) {
                if (!childIt2.hasNext()) {
                    //System.out.println("C");
                    childIt2.rewind();
                    i1 += 1;
                }
                //System.out.println(i1);
                if (i1 == page1.size()) break;
                next1 = page1.get(i1);
                boolean found = false;
                while (childIt2.hasNext()) {
                    next2 = childIt2.next();
                    //System.out.println("[]next1:" + next1 + "[]next2:" + next2 +"[]");
                    if (!switched && p.filter(next1, next2)) {
                        found = true;
                        break;
                    } else if (switched && p.filter(next2, next1)) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    Tuple next;
                    if (!switched) next = combineTuples(next1, next2);
                    else next = combineTuples(next2, next1);
                    return next;
                }
            }
        }
        return null;
    }

    @Override
    public DbIterator[] getChildren() {
        return new DbIterator[] {child1, child2};
    }

    @Override
    public void setChildren(DbIterator[] children) {
        child1 = children[0];
        child2 = children[1];
    }

}
