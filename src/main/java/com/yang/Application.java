package com.yang;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Slf4j
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
/*
 启动 pgvector 容器命令

    docker start pgvector
   验证容器是否运行： docker ps

进入docker里面的数据库
       docker exec -it pgvector psql -U postgres -d spring_ai

* */
       log.info("\n api接口文档：http://localhost:8090/doc.html");
    }
}
