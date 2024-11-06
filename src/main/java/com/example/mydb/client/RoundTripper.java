package com.example.mydb.client;

import com.example.mydb.transport.Package;
import com.example.mydb.transport.Packager;

public class RoundTripper {private Packager packager;

    public void RoundTripper(Packager packager) {
        this.packager = packager;
    }
    // 添加一个接受 Packager 参数的构造函数
    public RoundTripper(Packager packager) {
        this.packager = packager;
    }
    public Package roundTrip(Package pkg) throws Exception {
        packager.send(pkg);
        return packager.receive();
    }

    public void close() throws Exception {
        packager.close();
    }

}
