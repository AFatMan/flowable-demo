# flowable-engine流程设计器

## war方式

[下载地址](https://flowable.com/open-source/downloads/)

下载sources版,里面有个wars目录,把flowable-ui&flowable-rest war包,放入tomcat/webapps 中

启动tomcat

修改 \webapps\flowable-ui\WEB-INF\classes 目录下的properties文件

数据库修改为mysql

```
spring.datasource.driver-class-name=com.mysql.jdbc.Driver
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/flowable?characterEncoding=UTF-8
spring.datasource.username=root
spring.datasource.password=root

spring.datasource.type=org.apache.commons.dbcp.BasicDataSource
```


## 源码方式

官方github拉取最新版本

直接用idea打开

## modeler流程设计器

1. 切换到6.6.0-release分支
    - 点开maven有多个profile 选择`ui,deploy,check,h2mem`,此时发现flowable-ui会被加载成为子模块
    - 找到`flowable-ui-app`,修改配置文件`flowable-default.properties`,再启动主启动类

2. 可考虑将`flowable-ui-app`通过`mvn package -Pmysql -DskipTests`打包成war包
3. 或将`flowable-ui-app` copy到单独项目,通过项目运行


如果只用bpmn模块,flowable用法与activity用法相似 : [flowable官方文档-BPMN](https://flowable.com/open-source/docs/bpmn/ch02-GettingStarted/)

## BPMN主要api

```
ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();

RuntimeService runtimeService = processEngine.getRuntimeService();
RepositoryService repositoryService = processEngine.getRepositoryService();
TaskService taskService = processEngine.getTaskService();
ManagementService managementService = processEngine.getManagementService();
IdentityService identityService = processEngine.getIdentityService();
HistoryService historyService = processEngine.getHistoryService();
FormService formService = processEngine.getFormService();
DynamicBpmnService dynamicBpmnService = processEngine.getDynamicBpmnService();
```

flowable的api使用流式api调用的风格:

如:`taskService.createTaskQuery().taskAssignee(imCurrentUserName).processInstanceIdIn(ids).listPage(pageable.getPageNumber() * pageable.getPageSize(), pageable.getPageSize());`

### 历史

> 历史>运行时,凡是在运行时相关的表中存在的数据,一定存在于历史相对应表里

实例:`historyService.createHistoricProcessInstanceQuery().list()`
任务:`historyService.createHistoricTaskInstanceQuery().singleResult()`

更多请实际调用 `historyService.`


### 评论

评论都位于`TaskService`中使用

如
- 取 `taskService.getTaskComments()` 
- 存 `taskService.addComments()`



# SpringBootJPA & flowable

## 项目环境

> JPA已使用druid连接test1库

`application.yml`
```
spring:
  datasource:
    druid:
      db-type: com.alibaba.druid.pool.DruidDataSource
      driverClassName: net.sf.log4jdbc.sql.jdbcapi.DriverSpy
      #本地地址
      url: jdbc:log4jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:test1}?serverTimezone=Asia/Shanghai&characterEncoding=utf8&useSSL=false
      username: ${DB_USER:root}
      password: ${DB_PWD:root}
      # 初始连接数
      initial-size: 5
      # 最小连接数
      min-idle: 15
      # 最大连接数
      max-active: 30
      # 超时时间(以秒数为单位)
      remove-abandoned-timeout: 180
      # 获取连接超时时间
      max-wait: 3000
      # 连接有效性检测时间
      time-between-eviction-runs-millis: 60000
      # 连接在池中最小生存的时间
      min-evictable-idle-time-millis: 300000
      # 连接在池中最大生存的时间
      max-evictable-idle-time-millis: 900000
      # 指明连接是否被空闲连接回收器(如果有)进行检验.如果检测失败,则连接将被从池中去除
      test-while-idle: true
      # 指明是否在从池中取出连接前进行检验,如果检验失败, 则从池中去除连接并尝试取出另一个
      test-on-borrow: true
      # 是否在归还到池中前进行检验
      test-on-return: false
      # 检测连接是否有效
      validation-query: select 1
      # 配置监控统计
      webStatFilter:
        enabled: true
      stat-view-servlet:
        enabled: true
        url-pattern: /druid/*
        reset-enable: false
      filter:
        stat:
          enabled: true
          # 记录慢SQL
          log-slow-sql: true
          slow-sql-millis: 1000
          merge-sql: true
        wall:
          config:
            multi-statement-allow: true
```

## 此项目要求

1. flowable连接test2库
2. 在SpringBootJPA中正常使用flowable

## 1. 构建flowable数据连接池

> 目的: flowable使用hikari连接池连接test2库

### 修改 application.yml

```
# 最底下增加flowable配置

spring:
  datasource:
    druid:
...
...
...
# flowable工作流数据库配置
flowable:
  driverClassName: com.mysql.jdbc.Driver
  url: jdbc:mysql://localhost:3306/test2?serverTimezone=Asia/Shanghai&characterEncoding=utf8&useSSL=false

```

### 创建 FlowableDataSourceConfig.java

```java
package me.zhengjie.config.flowable;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flowable.app.spring.SpringAppEngineConfiguration;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.flowable.variable.service.impl.types.JPAEntityListVariableType;
import org.flowable.variable.service.impl.types.JPAEntityVariableType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Properties;

/**
 * flowable单独数据库配置
 *
 * @author TomcatZhuZhu
 */
@Configuration
public class FlowableDataSourceConfig {

    // 使用druid中配置的数据库账户名
    @Value("${spring.datasource.druid.username}")
    private String username;

    // 使用druid中配置的数据库密码
    @Value("${spring.datasource.druid.password}")
    private String password;

    @Value("${flowable.url}")
    private String url;

    @Value("${flowable.driverClassName}")
    private String driverClassName;

    private HikariDataSource flowableDataSource;

    private DataSource flowableDataSource() {
        if (flowableDataSource == null) {
            Properties properties = new Properties();
            properties.put("jdbcUrl", url);
            properties.put("username", username);
            properties.put("password", password);
            properties.put("driverClassName", driverClassName);
            HikariConfig configuration = new HikariConfig(properties);
            flowableDataSource = new HikariDataSource(configuration);
        }
        return flowableDataSource;
    }

    private PlatformTransactionManager transactionManager() {
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager();
        transactionManager.setDataSource(flowableDataSource);
        return transactionManager;
    }

    @Bean
    public EngineConfigurationConfigurer<SpringAppEngineConfiguration> engineConfigurer() {
        return configuration -> {
            // 设置自定义的hikari连接池
            configuration.setDataSource(flowableDataSource());
            // 事务管理器
            configuration.setTransactionManager(transactionManager());
            // 在构建流程引擎时，执行检查，并在必要时执行模式更新。
            configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
            // 设置流程变量的值支持jpa类型 解决异常:unknown variable type name jpa-entity
            configuration.setCustomPostVariableTypes(Arrays.asList(new JPAEntityVariableType(), new JPAEntityListVariableType()));
        };
    }
}
```

## 2. 业务测试

创建`ServiceImpl.java`

```java
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.RuntimeService;

/**
 * 简单实例
 */
@Service
@RequiredArgsConstructor
public class ServiceImpl implements Service{

   /**
    * 增删改查
    */
   private final Repository repository;

   private final RuntimeService runtimeService;

   @Override
   @Transactional(rollbackFor = Exception.class)
   public void testStartProcess(){
      //  启动流程 (流程需要自行提前在ui部署 或者 在项目resources文件夹下存放bpmn20.xml文件)
      ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("流程key", "流程变量map");
      // ② 情况二 Person1 数据库已限制name不能为null,此处保存报错
      // repository.save(new Person1(null));
      repository.save(new Person("tom"));
      // ③ 情况三
      // System.out.print(1/0)
   }
}

```

### 情况一

> 在testStartProcess()里全部代码无报错的前提下:成功保存Person 且 启动了 流程key的流程

### 情况二

> 在testStartProcess()里flowable代码无报错,但jpa报错下: flowable仍然添加了数据,jpa报错无添加数据

### 情况三

> 在testStartProcess()里flowable及jpa代码都无报错,但后续语句报错下: flowable仍然添加了数据,jpa报错无添加数据 结果与情况二一致

初步认为 jpa成功回滚,但flowable没有回滚成功

## 3. 针对flowable的事务回滚

> 将jpa并入到flowable的事务中 参考: org.flowable.common.engine.impl.interceptor.CommandContext

修改`ServiceImpl.java`

注入 `org.flowable.engine.ManagementService;`

```java
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.ManagementService;;

/**
 * 简单实例
 */
@Service
@RequiredArgsConstructor
public class ServiceImpl implements Service{

   /**
    * 增删改查
    */
   private final Repository repository;

   private final RuntimeService runtimeService;
   private final ManagementService managementService;

   @Override
   @Transactional(rollbackFor = Exception.class)
   public void testStartProcess(){
      managementService.executeCommand((Command<Void>) commandContext -> {
         
         // 启动流程 (流程需要自行提前在ui部署 或者 在项目resources文件夹下存放bpmn20.xml文件)
         ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("流程key", "流程变量map");
         // 保存jpa数据
         // ② 情况二 Person1 数据库已限制name不能为null,此处保存报错
         // repository.save(new Person1(null));
         repository.save(new Person("tom"));
         // ③ 情况三
         // System.out.print(1/0)
         return null;
      });
      
   }
}

```

> 此时,经测试,情况一二三都能正常回滚数据
