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
    
    File file;
    // We compute table id only once.
    int id;
    // We fetch tuple description only once.
    TupleDesc td;
    RandomAccessFile raf;
    HashMap<Integer, Boolean> freePages; // keeps track of the free pages
    
    public HeapFile(File f, TupleDesc td) {
        file = f;
        this.td = td;

        // This table id is recommended by getId function's comment.
        // TODO(wonjohn): in future, we may need to deal with a case
        // in which this hashcode is not unique.
        id = f.getAbsoluteFile().hashCode();

        try {
            raf = new RandomAccessFile(file, "rw");
            freePages = new HashMap<Integer, Boolean>();
            for (int i = 0; i < numPages(); i++) { // when the file is initialized, all of its pages are free
                freePages.put(i, true);
            } 
        } catch (FileNotFoundException ex) {
            // See https://piazza.com/class/hhrd9gio9n21s5?cid=202
            // This will never happen.
            ex.printStackTrace();
            System.exit(1);
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
        // if the end of file is reached,
        // data may not fully be filled but it is ok
        // because padding with zero (default of byte array)
        // is ok according to GSI.
        byte[] data = new byte[BufferPool.PAGE_SIZE];

        try {
            raf.seek(pid.pageNumber() * BufferPool.PAGE_SIZE);
            // Read *up to* BufferPool.PAGE_SIZE.
            raf.read(data);

            // Casting is possible due to
            // https://piazza.com/class/hhrd9gio9n21s5?cid=77
            return new HeapPage((HeapPageId) pid, data);
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
            // Never happens.
            // See https://piazza.com/class/hhrd9gio9n21s5?cid=202.
            return null;
        }
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        try {
            if (((HeapPage)page).getNumEmptySlots() > 0) { // update the page availability
                freePages.put(page.getId().pageNumber(), true);
            } else {
                freePages.put(page.getId().pageNumber(), false);
            }
            raf.seek(page.getId().pageNumber() * BufferPool.PAGE_SIZE);
            raf.write(page.getPageData());
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
            // Never happens.
            // See https://piazza.com/class/hhrd9gio9n21s5?cid=202.
        }
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        // TODO(wonjohn): what if file length is not divisible
        // by BufferPool.PAGE_SIZE?
        // See https://piazza.com/class/hhrd9gio9n21s5?cid=85
        return (int) Math.ceil((float) file.length() / BufferPool.PAGE_SIZE);
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        ArrayList<Page> modPages = new ArrayList<Page>();
        HeapPage freePage = null;
        // get the free page
        for (int i = 0; i < numPages(); i++) {
            if (freePages.get(i)) {
                HeapPageId pid = new HeapPageId(this.getId(), i);
                freePage = (HeapPage)Database.getBufferPool().getPage(tid, pid, Permissions.READ_WRITE);
                break;
            }
        }
        if (freePage != null) {
            freePage.insertTuple(t);
            freePage.markDirty(true, tid);
            if (freePage.getNumEmptySlots() > 0) {
                freePages.put(freePage.getId().pageNumber(), true);
            } else {
                freePages.put(freePage.getId().pageNumber(), false);
            }
            modPages.add(freePage);
        } else { // if there's no more free page, create a new page
            HeapPageId pid = new HeapPageId(id, numPages());
	    // System.out.println("numPages: " + numPages());
            HeapPage newPage = new HeapPage(pid, HeapPage.createEmptyPageData());
	    writePage(newPage);
	    newPage.insertTuple(t);
	    newPage.markDirty(true, tid);
	    if (newPage.getNumEmptySlots() > 0) {
		freePages.put(pid.pageNumber(), true);
	    } else {
		freePages.put(pid.pageNumber(), false);
	    }
	    //System.out.println("numPages: " + numPages());
	    modPages.add(newPage);
        }
        return modPages;
    }

    // see DbFile.java for javadocs
    public Page deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        RecordId rid = t.getRecordId();
        HeapPage page = (HeapPage)Database.getBufferPool().getPage(tid, rid.getPageId(), Permissions.READ_WRITE);
        page.deleteTuple(t);
        page.markDirty(true, tid);
        // mark the page as free
        freePages.put(page.getId().pageNumber(), true);
        return page;
    }

    // see DbFile.java for javadocs
    public DbFileIterator iterator(final TransactionId tid) {
        return new DbFileIterator() {
            // Get global bufferPool.
            BufferPool bp = Database.getBufferPool();
            int pageNumber;
            Iterator<Tuple> pageIt;

            public void open()
                throws DbException, TransactionAbortedException {
                // The iterator of the current page
                pageIt = null;
                // The current page's number
                pageNumber = 0;
                
                // To make check in hasNext easier,
                // we do our best to make page iterator (pageIt)
                // non-null.
                if (pageNumber < numPages()) {
                    pageIt = ((HeapPage)bp.getPage(
                        tid,
                        new HeapPageId(id, pageNumber),
                        null)).iterator();
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
                pageNumber = 0;
                pageIt = null;
            }
        };
    }

}

