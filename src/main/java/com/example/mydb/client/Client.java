package com.example.mydb.client;

import com.example.mydb.transport.Package;
import com.example.mydb.transport.Packager;

public class Client {
    private RoundTripper rt;

    public Client(Packager packager) {
        this.rt = new RoundTripper(packager);
    }

    public byte[] execute(byte[] stat) throws Exception {
        // 如果 Package 构造函数接受 byte[]，可以直接使用
        Package pkg = new Package(stat, null);
        Package resPkg = rt.roundTrip(pkg);
        if(resPkg.getErr() != null) {
            throw resPkg.getErr();
        }
        return resPkg.getData();
    }

    public void close() {
        try {
            rt.close();
        } catch (Exception e) {
            // 可以在这里记录异常日志
            e.printStackTrace();
        }
    }

}
