package com.example.mydb.backend.common.dm;

import com.example.mydb.backend.common.dm.PageCache.PageCache;
import com.example.mydb.backend.common.dm.dataItem.DataItem;
import com.example.mydb.backend.common.dm.logger.Logger;
import com.example.mydb.backend.common.dm.page.PageOne;
import com.example.mydb.backend.common.tm.TransactionManager;

public interface DataManager {
    DataItem read(long uid) throws Exception;
    long insert(long xid, byte[] data) throws Exception;
    void close();

    public static DataManager create(String path, long mem, TransactionManager tm) {
        PageCache pc = PageCache.create(path, mem);
        Logger lg = Logger.create(path);  // 使用自定义 Logger 的创建方法

        DataManagerImpl dm = new DataManagerImpl(pc, lg, tm);
        dm.initPageOne();
        return dm;
    }

    public static DataManager open(String path, long mem, TransactionManager tm) {
        PageCache pc = PageCache.open(path, mem);
        Logger lg = Logger.open(path);  // 使用自定义 Logger 的打开方法

        DataManagerImpl dm = new DataManagerImpl(pc, lg, tm);
        if (!dm.loadCheckPageOne()) {
            Recover.recover(tm, lg, pc);
        }
        dm.fillPageIndex();
        PageOne.setVcOpen(dm.pageOne);
        dm.pc.flushPage(dm.pageOne);

        return dm;
    }
}
