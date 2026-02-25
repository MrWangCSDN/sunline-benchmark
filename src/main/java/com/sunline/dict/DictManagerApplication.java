package com.sunline.dict;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 字典管理系统启动类
 */
@SpringBootApplication
@MapperScan("com.sunline.dict.mapper")
public class DictManagerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(DictManagerApplication.class, args);
        System.out.println("======================================");
        System.out.println("字典管理系统启动成功！");
        System.out.println("访问地址: http://localhost:8080");
        System.out.println("======================================");
    }
}

