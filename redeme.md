

代码模块

```
1. Transaction Manager (TM)   ==> 事务管理模块
2. Data Manager (DM)          ==> 数据管理模块
3. Version Manager (VM)       ==> 版本管理模块
4. Index Manager (IM)         ==> 索引管理模块
5. Table Manager (TBM)        ==> 表管理模块
```

# 启动



### **第一步：调整编译版本**

首先确保项目的编译版本适配 JDK：

```shell
<properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
</properties>
```

确认后，保存 <font color="red">`pom.xml`</font>，然后可以继续执行以下步骤



### **第二步：编译项目**

使用 Maven 编译项目源码：

```
mvn compile
```

确保终端已经定位到项目根目录，然后运行上述命令来编译所有源码。

### **第三步：创建数据库**

执行以下命令，以指定路径创建数据库（将 `/tmp/mydb` 替换为适合您的系统的路径，例如 `D:/mydatabase`）：

 <font color="blue">使用双引号包裹整个参数，并在内部使用单引号</font>

```shell
mvn exec:java "-Dexec.mainClass=com.example.mydb.backend.common.MydbApplication" "-Dexec.args='-create D:\coding\java\wang_db\tes'"

```

或者

```
mvn exec:java -Dexec.mainClass=com.example.mydb.backend.common.MydbApplication -Dexec.args="-create D:/coding/java/wang_db/tes"
```



### **第四步：启动数据库服务**

数据库创建完成后，使用以下命令来启动数据库服务：==会抛异常==

```shell
mvn exec:java "-Dexec.mainClass=com.example.mydb.backend.common.MydbApplication" "-Dexec.args='-open D:\coding\java\wang_db\tes'"
```

或者

```
mvn exec:java -Dexec.mainClass=com.example.mydb.backend.common.MydbApplication -Dexec.args="-open D:/coding/java/wang_db/tes"
```

这将会启动数据库服务并绑定到本地 9999 端口。

### **第五步：启动客户端**

在启动了数据库服务之后，需要再打开一个终端来启动数据库客户端。执行以下命令：

```shell
mvn exec:java -"Dexec.mainClass=com.example.mydb.client.Launcher"
```

这将启动一个交互式命令行，允许您通过类 SQL 语法与数据库进行交互。



==测试==

```
create table test table id int32, value int32(index id)
insert into test table values 10 33
select *from test table where id=10
```





# 一、页面索引与 DM 的实现

### 1. 页面索引设计分析

#### 1.1 锁机制与页面管理

- **锁机制**：`PageImpl` 使用 `ReentrantLock` 实现锁，用于确保对页面的操作是线程安全的。在多线程环境下，可能会有多个线程同时尝试修改页面的数据，因此需要通过锁机制来确保互斥访问。
- **页面脏标记（Dirty Page）**：`setDirty()` 和 `isDirty()` 提供了页面是否被修改的标记，用于在事务提交时识别哪些页面需要写回磁盘。被标记为脏页的页面将在适当的时机（如事务提交时）被刷写到磁盘，以保证数据的持久性。

#### 1.2 `PageOne` 的有效性检查

- 数据库有效性：

  PageOne通过在数据库启动和关闭时对特定位置的数据进行读写，用于判断数据库是否在上次关闭时发生了异常。

  - 如果 `100~107` 字节和 `108~115` 字节的内容不相同，则意味着数据库上次可能因为异常崩溃而未正常关闭，这时需要执行恢复流程。
  - 这种设计非常实用且高效，不需要额外维护复杂的元数据，只通过简单的数据写入和校验即可实现数据库有效性检查。

#### 1.3 资源管理

- **页面释放**：`release()` 方法通过调用 `PageCache` 中的 `release()` 方法，将页面释放回缓存。这种机制确保了内存中缓存的页面在使用完毕后能够及时释放，避免内存占用过多。
- **`PageCache` 管理**：`PageImpl` 依赖 `PageCache` 管理页面的生命周期。通过 `PageCache`，可以控制哪些页面被缓存，哪些页面需要刷写到磁盘。这种设计将页面的内存管理和生命周期控制从具体页面对象中解耦，使得缓存策略更加灵活。

#### 1.4. 总结

在 MYDB 的实现中，`Page` 和 `PageOne` 扮演着至关重要的角色：

- **`Page` 和 `PageImpl`**：
  - `Page` 是对数据库数据页的抽象，提供锁机制、页面释放和脏页标记等功能。
  - `PageImpl` 通过 `ReentrantLock` 实现锁机制，确保对页面的并发访问是线程安全的，并且通过 `PageCache` 进行页面的管理与释放。
- **`PageOne` 的特殊管理**：
  - `PageOne` 通过简单的有效性检查字节实现了数据库启动和关闭的有效性判断。
  - 这种设计使得数据库能够快速判断上一次是否正常关闭，如果不正常关闭则需要执行恢复操作，以保证数据一致性。

这些机制共同确保了数据库在多线程环境下的数据一致性和崩溃恢复能力，同时通过高效的页面管理和缓存策略，提升了数据库的性能。

# 二、死锁与事务隔离

`Entry` 类封装了一个数据项，并将其与事务管理紧密结合，实现了多版本控制（MVCC）的核心逻辑。主要功能如下：

- **生命周期管理**：通过 `XMIN` 和 `XMAX` 字段记录每个数据项的创建和删除事务。
- **读写操作**：提供数据的读取方法 `data()` 和设置删除标志的方法 `setXmax()`。
- **事务可见性支持**：通过 `XMIN` 和 `XMAX` 支持不同事务间的数据可见性控制。
- **封装底层 `DataItem`**：将底层的数据项封装成一个更易于管理和操作的对象，同时利用 `VersionManager` 进行版本控制和缓存管理。
- **缓存与版本控制**：通过对数据项的封装，以及利用 `VersionManager` 中的缓存机制，提高了数据库的读取效率，同时通过锁的使用保证了多事务环境中的数据安全。



**`VersionManagerImpl`** 是 MYDB 中用于管理数据版本控制和事务调度的关键组件。它用于在多事务环境中管理数据的读写操作，确保 MVCC（多版本并发控制）的一致性和事务隔离性。这是实现数据库事务调度、并发控制的关键部分，支持不同的隔离级别（如可重复读等），并维护事务的生命周期。

通过对事务的管理和数据项的版本控制，`VersionManagerImpl` 提供了 MVCC 机制，以确保数据库在高并发环境中的一致性和隔离性。

**读写数据**：提供对数据的读、插入、删除操作，确保每个数据操作都遵循事务的隔离级别。

**事务管理**：包括开启 (`begin`)、提交 (`commit`)、中止 (`abort`) 事务的功能。

**并发控制**：通过锁表和锁机制防止多个事务同时修改同一数据，防止并发冲突。

**缓存管理**：通过继承自 `AbstractCache<Entry>`，实现对 `Entry` 数据项的缓存，减少频繁的数据读取，提升性能。

# 三、IM索引管理

### 3.1. `BPlusTree` 类

`BPlusTree` 类是 B+ 树的整体管理器，它维护了树的根节点和与数据管理的接口。

#### 1.1 数据结构和构造方法

- **成员变量**:
  - **`DataManager dm`**：用于与数据管理层交互，提供对 `DataItem` 的操作接口。
  - **`long bootUid`**：根节点的唯一标识符（UID）。
  - **`DataItem bootDataItem`**：保存根节点的元数据。
  - **`Lock bootLock`**：用于保证对根节点的并发操作是线程安全的。
- **`create(DataManager dm)`**：
  - 创建 B+ 树时，首先创建一个空的根节点 (`newNilRootRaw()`)。
  - 使用 `DataManager` 将根节点插入数据库，并返回根节点的 UID。

```
public static long create(DataManager dm) throws Exception {
    byte[] rawRoot = Node.newNilRootRaw();
    long rootUid = dm.insert(TransactionManagerImpl.SUPER_XID, rawRoot);
    return dm.insert(TransactionManagerImpl.SUPER_XID, Parser.long2Byte(rootUid));
}
```

- `load(long bootUid, DataManager dm)`
  - 从已有的数据库中加载 B+ 树，通过 `bootUid` 定位根节点。
  - 创建一个新的 `BPlusTree` 对象，并初始化其根节点和其他必要的参数。

```
public static BPlusTree load(long bootUid, DataManager dm) throws Exception {
    DataItem bootDataItem = dm.read(bootUid);
    assert bootDataItem != null;
    BPlusTree t = new BPlusTree();
    t.bootUid = bootUid;
    t.dm = dm;
    t.bootDataItem = bootDataItem;
    t.bootLock = new ReentrantLock();
    return t;
}
```

### 3.2 基本操作

- `rootUid()`
  - 获取根节点的 UID，使用锁来确保多线程环境下访问的安全性。

```
private long rootUid() {
    bootLock.lock();
    try {
        SubArray sa = bootDataItem.data();
        return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start, sa.start + 8));
    } finally {
        bootLock.unlock();
    }
}
```

- `updateRootUid(long left, long right, long rightKey)`
  - 更新根节点，当树发生分裂时调用。
  - 创建新的根节点，并将新根的 UID 更新到 `bootDataItem`。

```
private void updateRootUid(long left, long right, long rightKey) throws Exception {
    bootLock.lock();
    try {
        byte[] rootRaw = Node.newRootRaw(left, right, rightKey);
        long newRootUid = dm.insert(TransactionManagerImpl.SUPER_XID, rootRaw);
        bootDataItem.before();
        SubArray diRaw = bootDataItem.data();
        System.arraycopy(Parser.long2Byte(newRootUid), 0, diRaw.raw, diRaw.start, 8);
        bootDataItem.after(TransactionManagerImpl.SUPER_XID);
    } finally {
        bootLock.unlock();
    }
}
```

- `search(long key)`和`searchRange(long leftKey, long rightKey)`
  - **`search(long key)`**：用于查找指定的键。
  - **`searchRange(long leftKey, long rightKey)`**：用于查找给定范围内的所有键，首先找到最左边的叶子节点，然后遍历叶子节点，直到超出右边界。

```java
public List<Long> search(long key) throws Exception {
    return searchRange(key, key);
}

public List<Long> searchRange(long leftKey, long rightKey) throws Exception {
    long rootUid = rootUid();
    long leafUid = searchLeaf(rootUid, leftKey);
    List<Long> uids = new ArrayList<>();
    while (true) {
        Node leaf = Node.loadNode(this, leafUid);
        LeafSearchRangeRes res = leaf.leafSearchRange(leftKey, rightKey);
        leaf.release();
        uids.addAll(res.uids);
        if (res.siblingUid == 0) {
            break;
        } else {
            leafUid = res.siblingUid;
        }
    }
    return uids;
}
```

- `insert(long key, long uid)`
  - 在 B+ 树中插入一个键值对，根节点可能会分裂，因此可能需要更新根节点。
  - 使用递归的方法在合适的节点插入数据，如果节点需要分裂，则逐层向上递归处理。

```
public void insert(long key, long uid) throws Exception {
    long rootUid = rootUid();
    InsertRes res = insert(rootUid, uid, key);
    assert res != null;
    if (res.newNode != 0) {
        updateRootUid(rootUid, res.newNode, res.newKey);
    }
}
```

### 3.3. `Node` 类

`Node` 类表示 B+ 树中的一个节点，节点可以是叶子节点，也可以是内部节点。它存储数据并提供操作和管理这些数据的方法。

#### 3.3.1 数据结构

- **Node 的结构**：
  - **`LeafFlag`**：标记节点是否为叶子节点。
  - **`KeyNumber`**：节点中键的数量。
  - **`SiblingUid`**：兄弟节点的 UID（用于连接叶子节点）。
  - **`[SonN][KeyN]`**：表示子节点 UID 和对应的键。
- **`BALANCE_NUMBER`**：B+ 树节点中保存键的数量平衡点。用于决定节点是否需要分裂。

#### 3.3.2 Node 操作方法

- **静态方法**（例如 `setRawIsLeaf`, `setRawNoKeys` 等）：
  - 用于操作节点的原始数据 (`SubArray`)，例如设置节点是否为叶子节点、设置兄弟节点 UID 等。
- **`loadNode(BPlusTree bTree, long uid)`**：
  - 加载节点，通过 UID 从 `DataManager` 中读取数据，解析出节点。

```
static Node loadNode(BPlusTree bTree, long uid) throws Exception {
    DataItem di = bTree.dm.read(uid);
    assert di != null;
    Node n = new Node();
    n.tree = bTree;
    n.dataItem = di;
    n.raw = di.data();
    n.uid = uid;
    return n;
}
```

- **`isLeaf()`**：判断节点是否是叶子节点，通过读锁确保并发访问的安全性。
- **`searchNext(long key)`** 和 **`leafSearchRange(long leftKey, long rightKey)`**：
  - **`searchNext(long key)`**：在内部节点中查找合适的子节点用于继续查找。
  - **`leafSearchRange(long leftKey, long rightKey)`**：在叶子节点中查找给定范围内的所有键。
- **`insertAndSplit(long uid, long key)`**：
  - 在节点中插入键值对，并处理节点的分裂。
  - **`before()`** 和 **`after()`** 操作用于保证数据的原子性，通过日志机制记录数据变更。

```
public InsertAndSplitRes insertAndSplit(long uid, long key) throws Exception {
    boolean success = false;
    Exception err = null;
    InsertAndSplitRes res = new InsertAndSplitRes();

    dataItem.before();
    try {
        success = insert(uid, key);
        if (!success) {
            res.siblingUid = getRawSibling(raw);
            return res;
        }
        if (needSplit()) {
            try {
                SplitRes r = split();
                res.newSon = r.newSon;
                res.newKey = r.newKey;
                return res;
            } catch (Exception e) {
                err = e;
                throw e;
            }
        } else {
            return res;
        }
    } finally {
        if (err == null && success) {
            dataItem.after(TransactionManagerImpl.SUPER_XID);
        } else {
            dataItem.unBefore();
        }
    }
}
```

- 节点分裂 (`split()`)
  - 当节点的键数量达到平衡点时，节点需要分裂。
  - 分裂会创建一个新的节点，并将原节点的一半键移到新节点中。

`BPlusTree` 和 `Node` 共同实现了 MYDB 中的 B+ 树索引。以下是实现的主要功能和特性：

1. **树管理** (`BPlusTree`)：
   - 维护了 B+ 树的根节点，并提供了创建、加载、插入、查找等操作。
   - 使用锁机制保证并发情况下的安全操作，确保对根节点的更新和读取都是互斥的。
2. **节点管理** (`Node`)：
   - `Node` 类实现了对节点的基本操作，包括设置和获取节点的各个字段、插入和查找、节点分裂等。
   - `Node` 使用 `DataItem` 提供的数据存储接口，数据修改前后通过 `before()` 和 `after()` 记录日志，保证数据操作的事务性。
   - 叶子节点之间通过 `SiblingUid` 实现链表结构，方便范围查找操作。
3. **线程安全**：
   - `BPlusTree` 和 `Node` 类均使用锁机制，确保在多线程环境下，对 B+ 树节点的读取和修改是安全的。
4. **日志与恢复**：
   - `Node` 类中的插入和分裂操作会对数据进行日志记录，这样在数据库崩溃恢复时，可以通过日志恢复一致性。
5. **递归结构**：
   - B+ 树的插入和查找通过递归的方式实现，插入时可能导致分裂，分裂可能进一步向上递归，最终更新根节点。

这个 B+ 树实现是 MYDB 的索引管理的一部分，能够高效地进行数据的查找、插入和范围查询。同时，它结合了事务和日志管理，确保在崩溃恢复时数据的一致性。



# 四、表管理

`TableManagerImpl` 类实现了表管理器，负责对表的增删改查，以及对事务的管理。它的主要职责如下：它维护了表的元数据以及各个表的缓存。这个类直接为数据库的最上层（如 Server 层）提供服务接口。

- 加载并缓存数据库中的所有表。
- 提供对表的 CRUD 操作的接口。
- 提供对事务的开始、提交和回滚的管理。

每个方法在执行操作前后，都使用互斥锁保证线程安全。此外，它维护了对事务的表访问缓存，以便事务提交或回滚时对相应的表进行处理。

### 4.1. 属性

- **`VersionManager vm`**：版本管理器，负责管理事务和版本控制。
- **`DataManager dm`**：数据管理器，负责与底层数据的交互。
- **`Booter booter`**：负责管理数据库的启动信息，如第一个表的 UID。
- **`Map<String, Table> tableCache`**：缓存了表名到 `Table` 对象的映射，用于快速访问表。
- **`Map<Long, List<Table>> xidTableCache`**：缓存了事务 ID 到该事务所涉及表的映射，用于管理各个事务中的表。
- **`Lock lock`**：互斥锁，用于保证对表缓存的线程安全操作。

# 五、服务

MYDB 的 C/S 结构使得它能够像 MySQL 一样，通过客户端与服务器通信执行 SQL 语句。其核心在于：

- 使用 `Package` 封装数据与异常。
- `Encoder` 用于编码和解码数据，`Transporter` 用于在网络上传输字节。
- 服务器使用线程池来处理每个客户端请求。
- `Executor` 调用 `TableManager` 来实际执行 SQL 语句，并返回结果给客户端。

### 客户端实现 (`Client`)

客户端的 `Launcher` 类是整个客户端应用的启动入口。它的作用是：

1. 创建与服务器的连接 (`Socket`)。
2. 通过网络传输器 (`Transporter`) 和编码器 (`Encoder`) 构建打包器 (`Packager`)。
3. 使用客户端 (`Client`) 和命令行 Shell (`Shell`) 来与服务器交互。

**Socket**：用于与服务器建立连接。

**Encoder 和 Transporter**：用于对数据进行编码和网络传输。

**Packager**：用于打包和解包客户端与服务器之间的通信。

**Shell**：提供一个交互式命令行，接受用户的输入，并将其通过 `Client` 发往服务器。

### 服务器实现 (`Server`)

服务器的 `Server` 类实现了服务器端的监听和处理客户端请求的逻辑。它使用 `ServerSocket` 监听端口，并为每个客户端连接创建一个新线程来处理请求。

- **ServerSocket**：用来监听指定的端口，等待客户端的连接请求。
- **线程池 (`ThreadPoolExecutor`)**：用于管理客户端的连接，最多允许同时处理 20 个请求，每个新连接都会交给线程池中的一个线程处理。

### 客户端请求处理 (`HandleSocket`)

`HandleSocket` 类实现了 `Runnable` 接口，用于处理来自客户端的请求。每个连接都会启动一个新线程，负责从客户端读取数据、处理数据，并将结果返回给客户端。

服务器端使用了 Java 的 `ThreadPoolExecutor` 作为线程池，来管理多个客户端的连接。

- `ThreadPoolExecutor`的参数：
  - 核心线程数为 10，最大线程数为 20。
  - 存活时间为 1 秒，使用 `ArrayBlockingQueue` 队列存放请求。
  - **任务策略**：使用 `CallerRunsPolicy`，即当线程池任务满时，由调用者线程执行任务。

### 总结

服务器端使用了 Java 的 `ThreadPoolExecutor` 作为线程池，来管理多个客户端的连接。

- `ThreadPoolExecutor`

   的参数：

  - 核心线程数为 10，最大线程数为 20。
  - 存活时间为 1 秒，使用 `ArrayBlockingQueue` 队列存放请求。
  - **任务策略**：使用 `CallerRunsPolicy`，即当线程池任务满时，由调用者线程执行任务。





# 其它



**Guava**：Google 提供的开源库，包含了大量实用的工具类和方法。在项目中，Guava 被用于处理字节数组操作，例如使用 `Bytes.concat()` 方法高效地合并多个 `byte[]` 数组。这对于数据库系统中需要频繁操作二进制数据的场景非常有帮助。此外，Guava 还提供了丰富的集合类和缓存工具，提升了开发效率和代码质量。

**Commons CLI**：Apache 提供的命令行解析库。项目使用 Commons CLI 来解析应用程序启动时的命令行参数，例如 `-create`、`-open`、`-mem` 等选项。这使得您的数据库应用程序可以通过命令行灵活配置，提升了用户体验和应用的可配置性。
