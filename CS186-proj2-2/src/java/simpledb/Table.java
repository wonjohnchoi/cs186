package simpledb;

import java.util.*;

/**
 * The table keeps track of DbFile, name, primary key field, and tuple desc.
 */

public class Table {

    DbFile dbfile;
    String name;
    String pkeyField;
    int tableID;
    TupleDesc tupleDesc;

    // Constructor.

    public Table(DbFile file, String name, String pkeyField) {
        this.dbfile = file;
        this.name = name;
        this.pkeyField = pkeyField;
        tableID = file.getId();
        tupleDesc = file.getTupleDesc();
    }

    public DbFile getDbFile() {
        return dbfile;
    }

    public String getName() {
        return name;
    }

    public String getPrimaryKey() {
        return pkeyField;
    }

    public int getTableId() {
        return tableID;
    }

    public TupleDesc getTupleDesc() {
        return tupleDesc;
    }
}
    