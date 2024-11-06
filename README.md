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

首先确保项目的编译版本适配您的 JDK。因为您提到使用 JDK 17，所以您需要确保 `pom.xml` 文件中的编译配置使用了 Java 17：

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

确保您的终端已经定位到项目根目录，然后运行上述命令来编译所有源码。

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

项目出处原作者：https://shinya.click/projects/mydb/mydb0
