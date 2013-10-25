package simpledb;
import java.util.*;

/** A class to represent a fixed-width histogram over a single integer-based field.
 */
public class IntHistogram {
    private int ntups;
    private int max;
    private int min;
    // number of buckets
    private int numBuckets;
    // size of a bucket
    private double bucketSize;
    // content of buckets
    private int[] buckets;

    /**
     * Get the start integer value of bucketIdx'th bucket.
     * @param bucketIdx the index of bucket we are interested in.
     */
    private double getBucketStart(int bucketIdx) {
        // This if stmt is unnecessary but I put this here
        // because I do not know Java's doubleing system well.
        if (bucketIdx == 0) return min;
        return min + bucketSize * bucketIdx;
    }

    /**
     * Get the end integer value of bucketIdx'th bucket.
     * @param bucketIdx the index of bucket we are interested in.
     */
    private double getBucketEnd(int bucketIdx) {
        // This if stmt is unnecessary but I put this here
        // because I do not know Java's doubleing system well.
        if (bucketIdx == numBuckets - 1) return max;
        return min + bucketSize * (bucketIdx + 1);
    }


    /**
     * Get the index of bucket a value belongs to.
     * @param val the value we are interested in.
     */
    private int getBucketIdx(int val) {
        // We define buckets in such a way that
        // its start is inclusive and its end is exclusive.
        // this is a special case because we don't want to make another bucket
        // just to contain max.
        if (val == max) return numBuckets - 1;
        // note that integer division rounds down.
        return (int) ((val - min) / bucketSize);
    }

    /**
     * Create a new IntHistogram.
     * 
     * This IntHistogram should maintain a histogram of integer values that it receives.
     * It should split the histogram into "buckets" buckets.
     * 
     * The values that are being histogrammed will be provided one-at-a-time through the "addValue()" function.
     * 
     * Your implementation should use space and have execution time that are both
     * constant with respect to the number of values being histogrammed.  For example, you shouldn't 
     * simply store every value that you see in a sorted list.
     * 
     * @param buckets The number of buckets to split the input value into.
     * @param min The minimum integer value that will ever be passed to this class for histogramming
     * @param max The maximum integer value that will ever be passed to this class for histogramming
     */
    @SuppressWarnings("unchecked")
    public IntHistogram(int buckets, int min, int max) {
    	this.numBuckets = buckets;
        this.min = min;
        this.max = max;
        this.ntups = 0;
        this.bucketSize = ((double) max - min) / numBuckets;
        // default values are zero.
        this.buckets = new int[numBuckets];
    }

    /**
     * Add a value to the set of values that you are keeping a histogram of.
     * @param v Value to add to the histogram
     */
    public void addValue(int v) {
    	++buckets[getBucketIdx(v)];
        ++ntups;
    }

    /**
     * Estimate the selectivity of a particular predicate and operand on this table.
     * 
     * For example, if "op" is "GREATER_THAN" and "v" is 5, 
     * return your estimate of the fraction of elements that are greater than 5.
     * 
     * @param op Operator
     * @param v Value
     * @return Predicted selectivity of this particular operator and value
     */
    public double estimateSelectivity(Predicate.Op op, int v) {
        if (op == Predicate.Op.NOT_EQUALS) return 1 - estimateSelectivity(Predicate.Op.EQUALS, v);
        if (op == Predicate.Op.GREATER_THAN_OR_EQ) {
            return estimateSelectivity(Predicate.Op.EQUALS, v)
                + estimateSelectivity(Predicate.Op.GREATER_THAN, v);
        }
        if (op == Predicate.Op.LESS_THAN_OR_EQ) {
            return estimateSelectivity(Predicate.Op.EQUALS, v)
                + estimateSelectivity(Predicate.Op.LESS_THAN, v);
        }
        
        if (op == Predicate.Op.EQUALS) {
            if (v < min || v > max) {
                return 0;
            }
        } else if (op == Predicate.Op.GREATER_THAN) {
            if (v < min) return 1;
            else if (v >= max) return 0;
        } else if (op == Predicate.Op.LESS_THAN) {
            if (v > max) return 1;
            else if (v <= min) return 0;
        }
        // TODO(wonjohn): fix above if we decide to support more operations.

        double selectivity;
        int idx = getBucketIdx(v);
        double start = getBucketStart(idx);
        double end = getBucketEnd(idx);
        int h = buckets[idx];
        double w = bucketSize;

        if (op == Predicate.Op.EQUALS) {
            selectivity = ((double) h / w) / ntups;
        } else if (op == Predicate.Op.GREATER_THAN) {
            selectivity = (double) h / ntups * (end - v) / w;
            for (int i = idx + 1; i < numBuckets; ++i) {
                selectivity += (double) buckets[i] / ntups;
            }
        } else if (op == Predicate.Op.LESS_THAN) {
            selectivity = (double) h / ntups * (v - start) / w;
            for (int i = 0; i < idx; ++i) {
                selectivity += (double) buckets[i] / ntups;
            }
        } else {
            // TODO(wonjohn): implement this if necessary.
            throw new UnsupportedOperationException("Didn't implement this operator: " + op);
        }
        return selectivity;
    }
    
    /**
     * @return
     *     the average selectivity of this histogram.
     *     
     *     This is not an indispensable method to implement the basic
     *     join optimization. It may be needed if you want to
     *     implement a more efficient optimization
     * */
    public double avgSelectivity()
    {
        // TODO(wonjohn): not sure what this is. figure this out.
        return 1.0;
    }
    
    /**
     * @return A string describing this histogram, for debugging purposes
     */
    public String toString() {

        // some code goes here
        return null;
    }
}
