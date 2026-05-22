package com.sunline.dict.config;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * 测试专用配置（test source path，不影响生产代码）。
 *
 * 背景：
 *   1. DictManagerApplication 上有 @MapperScan("com.sunline.dict.mapper")，
 *      MapperFactoryBean.setSqlSessionFactory() → SqlSessionTemplate 构造器
 *      → getEnvironment().getDataSource()，需要非 null 的 Environment + DataSource。
 *
 *   2. HealthController 通过 @Autowired 注入 DataSource，
 *      如果没有 DataSource bean，Spring 上下文加载失败。
 *
 *   3. Maven 使用 Java 25，Mockito Byte Buddy 不支持 Java 25，无法 mock()。
 *   4. 项目没有 H2 依赖，无法用 EmbeddedDatabaseBuilder。
 *
 * 方案：
 *   - 提供一个匿名内联 DataSource stub（满足类型要求，但 getConnection 未实现）
 *   - 提供一个带 Environment 的 SqlSessionFactory
 *   - 所有 @Bean 标记 @Primary，确保测试上下文中自动优先使用
 *
 * 注意：这是 @Configuration（非 @TestConfiguration），
 * @SpringBootApplication 的组件扫描会将其纳入测试 Spring 上下文。
 */
@org.springframework.context.annotation.Configuration
public class TestMybatisConfig {

    /**
     * 暴露 DataSource bean，满足 HealthController 的 @Autowired DataSource 注入。
     * 这个 stub 只提供类型实例，不真正连接数据库。
     */
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    public DataSource testDataSource() {
        return new DataSource() {
            @Override public Connection getConnection() { throw new UnsupportedOperationException("test stub"); }
            @Override public Connection getConnection(String u, String p) { throw new UnsupportedOperationException("test stub"); }
            @Override public <T> T unwrap(Class<T> iface) { return null; }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
            @Override public PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(PrintWriter out) {}
            @Override public void setLoginTimeout(int seconds) {}
            @Override public int getLoginTimeout() { return 0; }
            @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException {
                throw new SQLFeatureNotSupportedException();
            }
        };
    }

    /**
     * 提供 SqlSessionFactory，满足 MapperFactoryBean.checkDaoConfig()。
     * SqlSessionTemplate 构造时访问 environment.getDataSource()，
     * 因此 Environment 必须有非 null 的 DataSource。
     */
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    public SqlSessionFactory sqlSessionFactory(DataSource testDataSource) {
        ManagedTransactionFactory txFactory = new ManagedTransactionFactory();
        Environment env = new Environment("test", txFactory, testDataSource);
        Configuration config = new Configuration(env);
        return new DefaultSqlSessionFactory(config);
    }
}
