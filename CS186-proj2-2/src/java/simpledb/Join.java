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
    private List<Tuple> index2;
    private int i2;

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
        this.p = p;
        this.child1 = child1;
        this.child2 = child2;
        /*
        try {
            int i = 0;
            child1.open();
            while (child1.hasNext()) {
                child1.next();
                i += 1;
            }
            System.out.println("Size of child1: " + i);
            child1.close();

            child2.open();
            i = 0;
            while (child2.hasNext()) {
                i += 1;
                child2.next();
            }
            System.out.println("Size of index: " + i);
            child2.close();
        } catch (Exception ex) { /* fine to use Exception because this should never happen. 
            ex.printStackTrace();
            System.exit(1);
        }
        i2 = 0;*/
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
        child1.open();
        child2.open();
    }

    public void close() {
        super.close();
        child1.close();
        child2.close();
    }

    public void rewind() throws DbException, TransactionAbortedException {
        child1.rewind();
        child2.rewind();
        next1 = null;
    }


    private Tuple next1 = null;
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
        while (true) {
            //System.out.println("A");
            if (next1 == null) {
                if (child1.hasNext()) {
                    next1 = child1.next();
                    child2.rewind();
                } else {
                    return null;
                }
            } else if (!child2.hasNext()) {
                child2.rewind();
                if (child1.hasNext()) {
                    next1 = child1.next();
                    child2.rewind();                    
                } else {
                    return null;
                }
            }

            Tuple next2 = null;
            boolean found = false;

            while (child2.hasNext()) {
                next2 = child2.next();
                found = p.filter(next1, next2);
                //System.out.println(found);
                if (found) break;
            }

            /*
            for (int i = i2; i2 < index2.size(); i2++) {
                if (p.filter(next1, index2.get(i2))) {
                    next2 = index2.get(i2);
                    i2 += 1;
                    break;
                }
                }*/

            /*
              while (true) {
              if (p.filter(next1, next2) || !child2.hasNext()) break;
              next2 = child2.next();
              System.out.println(next2);
              }*/

            if (found) {
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
        }
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
