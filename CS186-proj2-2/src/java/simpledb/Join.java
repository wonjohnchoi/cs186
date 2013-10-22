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
    private List<Tuple> cachedPage;
    // The index of cached page that we are currently on.
    private int cachedIdx;
    // Maximum number of tuples that can be fit
    // into a page for each type of Tuple.
    private int numTuples1, numTuples2;
    // True iff all tuples in child1 fit in a page.
    private boolean fit1;
    // True iff all tuples in child2 fit in a page.
    private boolean fit2;
    // True iff we swapped child1 and child2.
    private boolean switched;
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
        numTuples1 = (int)((BufferPool.PAGE_SIZE * 8) / (tupleSize1 * 8 + 1));
        int tupleSize2 = child1.getTupleDesc().getSize();
        numTuples2 = (int)((BufferPool.PAGE_SIZE * 8) / (tupleSize2 * 8 + 1));

        this.p = p;
        this.child1 = child1;
        this.child2 = child2;
        cachedPage = new ArrayList<Tuple>();

        try {
            // See if all tuples in child1 fits
            // into a page. If so, store
            // all tuples into cachedPage.
            int i = 0;
            fit1 = true;
            child1.open();
            while (child1.hasNext()) {
                if (i >= numTuples1) {
                    fit1 = false;
                    cachedPage.clear();
                    break;
                }
                cachedPage.add(child1.next());
                i += 1;
            }
            child1.close();

            // If we cannot fit all tuples
            // in child1 into a page, we
            // try to fit all tuples in child2
            // into a page.
            if (!fit1) {
                fit2 = true;
                child2.open();
                i = 0;
                while (child2.hasNext()) {
                    if (i >= numTuples2) {
                        fit2 = false;
                        cachedPage.clear();
                        break;
                    }
                    i += 1;
                    cachedPage.add(child2.next());
                }
                child2.close();
            }
        } catch (Exception ex) {
            //OK to use Exception because this should never happen.
            ex.printStackTrace();
            System.exit(1);
        }

        if (fit1) {
            System.out.println("Best!");
            switched = false;
        } else if (fit2) {
            // swap child1 and child2
            // to put one that fits into a page
            // to be on the outer loop.
            DbIterator tmp2;
            tmp2 = child1;
            child1 = child2;
            child2 = tmp2;

            int tmp;
            tmp = numTuples1;
            numTuples1 = numTuples2;
            numTuples2 = tmp;
            System.out.println(child1.getTupleDesc());
            System.out.println(child2.getTupleDesc());
            System.out.println("Switched");
            p = new JoinPredicate(p.getField2(), p.getOperator(), p.getField1());

            switched = true;
        } else {
            System.out.println("Neither");
            switched = false;
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
        child1.open();
        child2.open();
        cachedIdx = 0;
    }

    public void close() {
        super.close();
        child1.close();
        child2.close();
    }

    public void rewind() throws DbException, TransactionAbortedException {
        child1.rewind();
        child2.rewind();
        cachedIdx = 0;
        next1 = null;
    }

    private Tuple next1 = null;

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
        Tuple next2 = null;
        while (true) {
            if (!child2.hasNext()) {
                child2.rewind();
                cachedIdx += 1;
            }
            if (cachedIdx >= cachedPage.size()) {
                if (fit1 || fit2) break;
                else if (!child1.hasNext()) break;
                else {
                    cachedPage.clear();
                    cachedIdx = 0;
                    for (int i = 0; i < numTuples1 && child1.hasNext(); i++) {
                        cachedPage.add(child1.next());
                    }
                    child2.rewind();
                }
            }
            next1 = cachedPage.get(cachedIdx);
            boolean found = false;
            while (child2.hasNext()) {
                next2 = child2.next();
                System.out.println("next1:"+next1+"|next2:"+next2);
                System.out.println(p.getField1() + " " + p.getField2());
                
                if (p.filter(next1, next2)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                return combineTuples(next1, next2);
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
