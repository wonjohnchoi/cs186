package simpledb;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

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
    // Manages locks
    private Locks locks;

    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */
    public BufferPool(int numPages) {
        this.numPages = numPages;
        pidToPage = new HashMap<PageId, Page>();
        locks = new Locks();
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
    public synchronized Page getPage(TransactionId tid, PageId pid, Permissions perm)
        throws TransactionAbortedException, DbException {
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
                ex.printStackTrace();
            }
            pidToPage.put(pid, page);
        }
        // Measure how many seconds it takes for the desired permission
        // is acquired. If it takes more than .5 seconds,
        // abort the current transaction.
        long startTime = System.currentTimeMillis();
        boolean acquired = false;
        if (perm.equals(Permissions.READ_ONLY)) { // acquire shared lock
            acquired = locks.acquire(tid, pid, false);
            while (!acquired) {
                if (System.currentTimeMillis() > startTime + 2000) {
                    try {
                        transactionComplete(tid, false);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    System.out.println("Shared");
                    throw new TransactionAbortedException();
                }
                acquired = locks.acquire(tid, pid, false);
            }
        } else { // acquire exclusive lock
            acquired = locks.acquire(tid, pid, true);
            while (!acquired) {
                if (System.currentTimeMillis() > startTime + 2000) {
                    pidToPage.get(pid).markDirty(false, tid);
                    try {
                        transactionComplete(tid, false);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    System.out.println("Exclusive");
                    throw new TransactionAbortedException();
                }
                acquired = locks.acquire(tid, pid, true);
            }
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
    public synchronized void releasePage(TransactionId tid, PageId pid) {
        locks.release(tid, pid);
    }

    /**
     * Release all locks associated with a given transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     */
    public synchronized void transactionComplete(TransactionId tid) throws IOException {
        locks.releaseLocks(tid);
    }

    /** Return true if the specified transaction has a lock on the specified page */
    public synchronized boolean holdsLock(TransactionId tid, PageId p) {
        return locks.holdsLock(tid, p);
    }

    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
 n     *
     * @param tid the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */
    public synchronized void transactionComplete(TransactionId tid, boolean commit)
        throws IOException {
        // release all locks associated with tid
        transactionComplete(tid);
        // commit
        if (commit) {
            flushPages(tid);
        } else {
            // abort
            // logic for recovery
            for (PageId pid : pidToPage.keySet()) {
                Page page = pidToPage.get(pid);
                TransactionId dirtyTid = page.isDirty();
                if (dirtyTid != null && dirtyTid.equals(tid)) {
                    page.markDirty(false, dirtyTid);
                    pidToPage.put(pid, page.getBeforeImage());
                }
            }
        }
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
    public synchronized void insertTuple(TransactionId tid, int tableId, Tuple t)
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
    public synchronized void deleteTuple(TransactionId tid, Tuple t)
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
        page.markDirty(false, page.isDirty());
        DbFile dbFile = Database.getCatalog().getDbFile(pid.getTableId());
        dbFile.writePage(page);
    }

    /** Write all pages of the specified transaction to disk.
     */
    public synchronized  void flushPages(TransactionId tid) throws IOException {
        for (PageId pid : pidToPage.keySet()) {
            Page page = pidToPage.get(pid);
            TransactionId dirtyTid = page.isDirty();
            if (dirtyTid != null && dirtyTid.equals(tid)) {
                flushPage(pid);
            }
        }
    }

    /**
     * Discards a page from the buffer pool.
     * Flushes the page to disk to ensure dirty pages are updated on disk.
     */
    private synchronized void evictPage() throws DbException {
        PageId pid = null;
        for (PageId pid0 : pidToPage.keySet()) {
            // clean
            if (pidToPage.get(pid0).isDirty() == null) {
                pid = pid0;
            }
        }
        // if all pages are dirty, throw DbException
        if (pid == null) {
            throw new DbException("No page to evict!");
        }
        // flush before removing pid from pidToPage because flush uses pid.
        try {
            flushPage(pid);
        } catch (IOException ex) {
            throw new DbException("Cannot flush page");
        }
        assert (pidToPage.remove(pid) != null);
    }
    
    // private class that manages the locks
    private class Locks {
        // shared locks
        ConcurrentHashMap<PageId, ConcurrentLinkedQueue<TransactionId>> shared;
        // exclusive locks
        ConcurrentHashMap<PageId, TransactionId> exclusive;
        // map tid to the list of locks the transaction is holding
        ConcurrentHashMap<TransactionId, ConcurrentLinkedQueue<PageId>> tidLocks;
        // constructor
        boolean debug = true;
        private Locks() {
            shared = new ConcurrentHashMap<PageId, ConcurrentLinkedQueue<TransactionId>>();
            exclusive = new ConcurrentHashMap<PageId, TransactionId>();
            tidLocks = new ConcurrentHashMap<TransactionId, ConcurrentLinkedQueue<PageId>>();
        }

        // acquire the lock for the specific page. Returns true if the lock is acquired.
        private synchronized boolean acquire(TransactionId tid, PageId pid, boolean exc) {
            // acquire the lock for the specific page
            ConcurrentLinkedQueue<TransactionId> tids = shared.get(pid);
            TransactionId excTid = exclusive.get(pid);   
            if (exc) { // handles exclusive lock
                if (tids != null && ((tids.size() == 1 && !tids.contains(tid)) || tids.size() > 1)) {
                    if (debug) {
                        for (TransactionId tid0 : tids)
                            System.out.println(tid0.getId());
                        System.out.println("There are one or more shared locks that are not my tid: " + tid.getId());
                        debug = false;
                    }
                    return false;
                }
                if (excTid != null && !excTid.equals(tid)) {
                    if (debug) {
                        System.out.println("There are execute lock that is not mine");
                        debug = false;
                    }
                    return false;
                }
               ConcurrentLinkedQueue<PageId> pids = tidLocks.get(tid);
                if (pids == null) {
                    pids = new ConcurrentLinkedQueue<PageId>();
                }
                if (!pids.contains(pid))
                    pids.add(pid);
                if (tids != null && tids.contains(tid)) {
                    tids.remove(tid);
                }
                tidLocks.put(tid, pids);
                exclusive.put(pid, tid);
                return true;
            } else { // handles shared lock
                if (excTid == null || excTid.equals(tid)) {
                    if (tids == null) {
                        tids = new ConcurrentLinkedQueue<TransactionId>();
                    }
                    ConcurrentLinkedQueue<PageId> pids = tidLocks.get(tid);
                    if (pids == null) {
                        pids = new ConcurrentLinkedQueue<PageId>();
                    }
                    if (!pids.contains(pid))
                        pids.add(pid);
                    tidLocks.put(tid, pids);
                    if (excTid == null && !tids.contains(tid)) {
                        tids.add(tid);
                        shared.put(pid, tids);
                    }
                    return true;
                }
            }
            return false;
        }

        // release the lock.
        private synchronized void release(TransactionId tid, PageId pid) {
            ConcurrentLinkedQueue<TransactionId> tids = shared.get(pid);
            TransactionId excTid = exclusive.get(pid);
            if (tids != null) {
                tids.remove(tid);
                shared.put(pid, tids);
            }
            exclusive.remove(pid);
        }

        private synchronized void releaseLocks(TransactionId tid) {
            ConcurrentLinkedQueue<PageId> pids = tidLocks.get(tid);
            if (pids == null) return;
            for (PageId pid : pids) {
                release(tid, pid);
            }
            tidLocks.remove(tid);
        }

        private synchronized boolean holdsLock(TransactionId tid, PageId pid) {
            ConcurrentLinkedQueue tids = shared.get(pid);
            TransactionId excTid = exclusive.get(pid);
            if (tids != null) { // checks for shared lock
                if (tids.contains(tid)) {
                    return true;
                }
            }
            if (excTid != null) { // checks for exclusive lock
                return true;
            }
            return false;
        }
    }
}