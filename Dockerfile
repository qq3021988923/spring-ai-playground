FROM eclipse-temurin:17-jre
WORKDIR /app

# 直接把本地打好的 jar 拷进去
COPY target/springai-lianxi-0.0.1-SNAPSHOT.jar app.jar

# 对话记忆、文件下载 存放目录
RUN mkdir -p /app/tmp/chat-memory /app/tmp/file /app/tmp/download

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
