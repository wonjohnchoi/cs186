package simpledb;

import java.io.*;
import java.util.*;

/**
 * BufferPool manages the reading and writing of pages into memory from
 * disk. Access methods call into it to retrieve pages, and it fetches
 * pages from the appropriate location.
 * <p>
 * The BufferPool is also responsible for locking;  when a transaction fetches
 * a page, BufferPool checks that the transaction has the appropriate
 * locks to read/write the page.
 */
public class BufferPool {
    /** Bytes per page, including header. */
    public static final int PAGE_SIZE = 4096;

    /** Default number of pages passed to the constructor. This is used by
        other classes. BufferPool should use the numPages argument to the
        constructor instead. */
    public static final int DEFAULT_PAGES = 50;
    private int numPages;
    private HashMap<PageId, Page> pidToPage;
    private TreeMap<Long, PageId> timeToPid;

    /*
      static class PageWithTime implements Comparable<PageWithTime> {
      Page page;
      long time;
      public PageWithTime(Page page, long time) {
      this.page = page;
      this.time = time;
      }

      public int compareTo(PageWithTime pageWithTime) {
      return time - pageWithTime.time;
      }
      }*/

    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */
    public BufferPool(int numPages) {
        this.numPages = numPages;
        pidToPage = new HashMap<PageId, Page>();
        timeToPid = new TreeMap<Long, PageId>();
    }

    /**
     * Retrieve the specified page with the associated permissions.
     * Will acquire a lock and may block if that lock is held by another
     * transaction.
     * <p>
     * The retrieved page should be looked up in the buffer pool.  If it
     * is present, it should be returned.  If it is not present, it should
     * be added to the buffer pool and returned.  If there is insufficient
     * space in the buffer pool, an page should be evicted and the new page
     * should be added in its place.
     *
     * @param tid the ID of the transaction requesting the page
     * @param pid the ID of the requested page
     * @param perm the requested permissions on the page
     */
    public  Page getPage(TransactionId tid, PageId pid, Permissions perm)
        throws TransactionAbortedException, DbException {
        // TODO(wonjohn): Should we ignore tid and perm?
        // For now, yes; see https://piazza.com/class/hhrd9gio9n21s5?cid=70

        // Check if we have cached page.
        if (!pidToPage.containsKey(pid)) {
            // If new page is requested and BufferPool is full, throw exception.
            if (pidToPage.size() == numPages) {
                evictPage();
            }
            
            // Fetch page.
            HeapPage page = null;
            try {
                page = (HeapPage)Database.getCatalog().getDbFile(pid.getTableId()).readPage(pid);
            } catch (IllegalArgumentException ex) {
                try {
                    // allocating a new page
                    page = new HeapPage((HeapPageId)pid, HeapPage.createEmptyPageData());
                    //Database.getCatalog().getDbFile(pid.getTableId()).writePage(page);
                } catch (IOException ex2) {}
            }
            pidToPage.put(pid, page);
            timeToPid.put(System.currentTimeMillis(), pid);
        }
        return pidToPage.get(pid);
    }

    /**
     * Releases the lock on a page.
     * Calling this is very risky, and may result in wrong behavior. Think hard
     * about who needs to call this and why, and why they can run the risk of
     * calling it.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param pid the ID of the page to unlock
     */
    public  void releasePage(TransactionId tid, PageId pid) {
        // some code goes here
        // not necessary for proj1
    }

    /**
     * Release all locks associated with a given transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     */
    public void transactionComplete(TransactionId tid) throws IOException {
        // some code goes here
        // not necessary for proj1
    }

    /** Return true if the specified transaction has a lock on the specified page */
    public boolean holdsLock(TransactionId tid, PageId p) {
        // some code goes here
        // not necessary for proj1
        return false;
    }

    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */
    public void transactionComplete(TransactionId tid, boolean commit)
        throws IOException {
        // some code goes here
        // not necessary for proj1
    }

    /**
     * Add a tuple to the specified table behalf of transaction tid.  Will
     * acquire a write lock on the page the tuple is added to(Lock 
     * acquisition is not needed for lab2). May block if the lock cannot 
     * be acquired.
     * 
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and updates cached versions of any pages that have 
     * been dirtied so that future requests see up-to-date pages. 
     *
     * @param tid the transaction adding the tuple
     * @param tableId the table to add the tuple to
     * @param t the tuple to add
     */
    public void insertTuple(TransactionId tid, int tableId, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        DbFile file = Database.getCatalog().getDbFile(tableId);
        file.insertTuple(tid, t);
    }

    /**
     * Remove the specified tuple from the buffer pool.
     * Will acquire a write lock on the page the tuple is removed from. May block if
     * the lock cannot be acquired.
     *
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit.  Does not need to update cached versions of any pages that have 
     * been dirtied, as it is not possible that a new page was created during the deletion
     * (note difference from addTuple).
     *
     * @param tid the transaction adding the tuple.
     * @param t the tuple to add
     */
    public  void deleteTuple(TransactionId tid, Tuple t)
        throws DbException, TransactionAbortedException {
        PageId pid = t.getRecordId().getPageId();
        DbFile file = Database.getCatalog().getDbFile(pid.getTableId());
        file.deleteTuple(tid, t);
    }

    /**
     * Flush all dirty pages to disk.
     * NB: Be careful using this routine -- it writes dirty data to disk so will
     *     break simpledb if running in NO STEAL mode.
     */
    public synchronized void flushAllPages() throws IOException {
        for (PageId pid : pidToPage.keySet()) {
            flushPage(pid);
        }
    }

    /** Remove the specific page id from the buffer pool.
        Needed by the recovery manager to ensure that the
        buffer pool doesn't keep a rolled back page in its
        cache.
    */
    public synchronized void discardPage(PageId pid) {
        // some code goes here
        // not necessary for proj1
    }

    /**
     * Flushes a certain page to disk
     * @param pid an ID indicating the page to flush
     */
    private synchronized  void flushPage(PageId pid) throws IOException {
        Page page = pidToPage.get(pid);
        TransactionId dirtyTid = page.isDirty();
        if (dirtyTid != null) {
            // TODO(wonjohn): what should we use as transaction id?
            // Currently, it is set to tid that dirtied this page before.
            page.markDirty(false, dirtyTid);
            DbFile dbFile = Database.getCatalog().getDbFile(pid.getTableId());
            dbFile.writePage(page);
        }        
    }

    /** Write all pages of the specified transaction to disk.
     */
    public synchronized  void flushPages(TransactionId tid) throws IOException {
        // some code goes here
        // not necessary for proj1
    }

    /**
     * Discards a page from the buffer pool.
     * Flushes the page to disk to ensure dirty pages are updated on disk.
     */
    private synchronized  void evictPage() throws DbException {
        Map.Entry<Long, PageId> timeAndPid = timeToPid.pollFirstEntry();
        if (timeAndPid == null) {
            throw new DbException("No page to evict!");
        }
        PageId pid = timeAndPid.getValue();
        // flush before removing pid from pidToPage because flush uses pid.
        try {
            flushPage(pid);
        } catch (IOException ex) {
            throw new DbException("Cannot flush page");
        }

        if (pidToPage.remove(pid) == null) {
            System.out.println("There is an error in BufferPool.");
            System.exit(1);
        }
    }

}
