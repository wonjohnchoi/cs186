package simpledb;

import java.io.*;
import java.util.*;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 * 
 * @see simpledb.HeapPage#HeapPage
 * @author Sam Madden
 */
public class HeapFile implements DbFile {

    /**
     * Constructs a heap file backed by the specified file.
     * 
     * @Param f
     *            the file that stores the on-disk backing store for this heap
     *            file.
     */
    
    ArrayList<PageId> pids = new ArrayList<PageId>();
    File file;
    int id;
    TupleDesc td;
    RandomAccessFile raf;
    /*
    byte header[];
    HeapPage pages[];
    int numSlots;
    */
    
    public HeapFile(File f, TupleDesc td) {
        file = f;
        this.td = td;
        // This is recommended by getId function's comment.
        id = f.getAbsoluteFile().hashCode();
        try {
            raf = new RandomAccessFile(file, "rw");
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
            System.exit(1);
            // TODO(wonjohn): find out what to do here.
        }
    }

    /**
     * Returns the File backing this HeapFile on disk.
     * 
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        return file;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     * 
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     * 
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        return td;
    }

    // see DbFile.java for javadocs
    public Page readPage(PageId pid) {
        byte[] data = new byte[BufferPool.PAGE_SIZE * 8];
        try {
            raf.seek(pid.pageNumber() * BufferPool.PAGE_SIZE);
            raf.read(data);

            // Casting is possible due to
            // https://piazza.com/class/hhrd9gio9n21s5?cid=77
            return new HeapPage((HeapPageId) pid, data);
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
            // TODO(wonjohn): find out what to do here.
        }
        return null;
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        try {
            raf.seek(page.getId().pageNumber() * BufferPool.PAGE_SIZE * 8);
            raf.write(page.getPageData());
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
            // TODO(wonjohn): find out what to do here.
        }
        pids.add(page.getId());
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        long size = file.length();
        // TODO(wonjohn): fix this.
        return (int)(size / BufferPool.PAGE_SIZE);
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        return null;
        // not necessary for proj1
    }

    // see DbFile.java for javadocs
    public Page deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        // some code goes here
        return null;
        // not necessary for proj1
    }

    // see DbFile.java for javadocs
    public DbFileIterator iterator(final TransactionId tid) {
        return new DbFileIterator() {
            BufferPool bp;
            int pageNumber;
            Iterator<Tuple> pageIt;

            public void open()
                throws DbException, TransactionAbortedException {
                bp = new BufferPool(numPages());
                pageIt = null;
                pageNumber = 0;
                
                if (pageNumber < numPages()) {
                    pageIt = ((HeapPage)bp.getPage(tid, new HeapPageId(id, pageNumber), null)).iterator();
                    pageNumber += 1;
                }
            }
            public boolean hasNext()
                throws DbException, TransactionAbortedException {
                if (pageIt == null) {
                    return false;
                }
                while (!pageIt.hasNext() && pageNumber < numPages()) {
                    pageIt = ((HeapPage)bp.getPage(tid, new HeapPageId(id, pageNumber), null)).iterator();
                    pageNumber += 1;
                } 
                return pageIt.hasNext();
            }
            public Tuple next()
                throws DbException, TransactionAbortedException, NoSuchElementException {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return pageIt.next();
            }
            public void rewind() throws DbException, TransactionAbortedException {
                close();
                open();
            }
            
            public void close() {
                bp = null;
                pageNumber = 0;
                pageIt = null;
            }
        };
    }

}

