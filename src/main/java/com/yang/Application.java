package com.yang;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Slf4j
public class Application {
    /// docker compose down 【-v】 → 容器删除 + 数据删除 ⚠️ 禁止操作
    // docker compose down 这个容器
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
/*
在当前根目录打开窗口
容器启动命令 会先检查容器是否存在，如果存在就直接启动
    docker compose up pgvector -d

 之后 可以使用 docker-compose start 启动
停止 docker-compose stop
进入docker里面的数据库
           docker exec -it yu-ai-pgvector psql -U postgres -d yu_ai_db
        select count(*) from vector_store;

        // 本地模型启动命令 ollama serve

        // 项目前后端+环境已经打包成镜像 根目录执行 docker compose up -d 每次启动直接执行这个命令就能跑起来！
         // docker 控制台 docker compose logs -f app
         // 单独关闭这个镜像  docker stop yu-ai-app
* */
       log.info("\n api接口文档：http://localhost:8090/doc.html");
    }
}
