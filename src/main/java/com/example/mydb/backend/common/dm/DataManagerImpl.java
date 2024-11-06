package com.example.mydb.backend.common.dm;

import com.example.mydb.backend.common.AbstractCache;
import com.example.mydb.backend.common.dm.PageCache.PageCache;
import com.example.mydb.backend.common.dm.dataItem.DataItem;
import com.example.mydb.backend.common.dm.logger.Logger;
import com.example.mydb.backend.common.dm.page.Page;
import com.example.mydb.backend.common.dm.page.PageOne;
import com.example.mydb.backend.common.dm.page.PageX;
import com.example.mydb.backend.common.dm.pageIndex.PageIndex;
import com.example.mydb.backend.common.dm.pageIndex.PageInfo;
import com.example.mydb.backend.common.tm.TransactionManager;
import com.example.mydb.backend.common.utils.Panic;

import java.util.List;

public class DataManagerImpl extends AbstractCache<DataItem> implements DataManager {

    TransactionManager tm;
    PageCache pc;
    Logger logger;  // 使用自定义的 Logger
    PageIndex pIndex;
    Page pageOne;

    public DataManagerImpl(PageCache pc, Logger logger, TransactionManager tm) {
        super(0);
        this.pc = pc;
        this.logger = logger;  // 使用自定义的 Logger
        this.tm = tm;
        this.pIndex = new PageIndex();
    }
    public void logDataItem(long xid, DataItem di) {
        byte[] log = Recover.updateLog(xid, di);
        logger.log(log);
    }

    public void releaseDataItem(DataItem di) {
        super.release(di.getUid());
    }

    @Override
    public DataItem read(long uid) throws Exception {
        DataItem dataItem = super.get(uid);
        if (dataItem == null) {
            return null;
        }
        return dataItem;
    }

    @Override
    public long insert(long xid, byte[] data) throws Exception {
        byte[] raw = DataItem.wrapDataItemRaw(data);
        if (raw.length > PageX.MAX_FREE_SPACE) {
            throw new RuntimeException("Data too large");
        }

        PageInfo pi = pIndex.select(raw.length);
        if (pi == null) {
            int newPgno = pc.newPage(PageX.initRaw());
            pIndex.add(newPgno, PageX.MAX_FREE_SPACE);
            pi = pIndex.select(raw.length);
            if (pi == null) {
                throw new RuntimeException("Database busy");
            }
        }

        Page pg = null;
        try {
            pg = pc.getPage(pi.pgno);
            logger.log(raw);  // 记录日志
            short offset = PageX.insert(pg, raw);
            pg.release();
            return (long) pi.pgno << 32 | (offset & 0xFFFF);
        } finally {
            if (pg != null) {
                pIndex.add(pi.pgno, PageX.getFreeSpace(pg));
            }
        }
    }

    @Override
    public void close() {
        logger.close();  // 关闭日志
        PageOne.setVcClose(pageOne);
        pageOne.release();
        pc.close();
    }

    // 实现 AbstractCache 的抽象方法
    @Override
    protected void releaseForCache(DataItem dataItem) {
        dataItem.page().release();  // 释放与数据项相关的页面资源
    }

    @Override
    protected DataItem getForCache(long uid) throws Exception {
        short offset = (short) (uid & ((1L << 16) - 1));
        uid >>>= 32;
        int pgno = (int) (uid & ((1L << 32) - 1));
        Page pg = pc.getPage(pgno);
        return DataItem.parseDataItem(pg, offset, this);
    }

    // 初始化 PageOne
    void initPageOne() {
        int pgno = pc.newPage(PageOne.InitRaw());
        assert pgno == 1;
        try {
            pageOne = pc.getPage(pgno);
        } catch (Exception e) {
            Panic.panic(e);
        }
        pc.flushPage(pageOne);
    }

    // 加载并检查 PageOne
    boolean loadCheckPageOne() {
        try {
            pageOne = pc.getPage(1);
        } catch (Exception e) {
            Panic.panic(e);
        }
        return PageOne.checkVc(pageOne);
    }

    // 填充 pageIndex
    void fillPageIndex() {
        int pageNumber = pc.getPageNumber();
        for (int i = 2; i <= pageNumber; i++) {
            Page pg = null;
            try {
                pg = pc.getPage(i);
            } catch (Exception e) {
                Panic.panic(e);
            }
            pIndex.add(pg.getPageNumber(), PageX.getFreeSpace(pg));
            pg.release();
        }
    }
}
