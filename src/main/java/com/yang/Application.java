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
在当前根目录打开窗口
容器启动命令 会先检查容器是否存在，如果存在就直接启动 docker-compose up -d
stop 之后 可以使用 docker-compose start 启动
停止 docker-compose stop
进入docker里面的数据库
       docker exec -it yu-ai-pgvector psql -U postgres -d yu_ai_db

        select count(*) from vector_store;
* */
       log.info("\n api接口文档：http://localhost:8090/doc.html");
    }
}
