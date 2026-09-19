# ========== 阶段1：构建（用自带 Maven 的 JDK 镜像，不依赖本机是否装了 mvn） ==========
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# 先只拷 pom.xml，利用 Docker 层缓存：pom 不变就不重新下载依赖
COPY pom.xml .
RUN mvn -B -e dependency:go-offline

# 再拷入源码并打包（跳过测试以加速构建）
COPY src ./src
RUN mvn -B -e clean package -DskipTests

# ========== 阶段2：运行（仅 JRE，不含编译工具和源码，镜像更小更安全） ==========
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Shanghai

# 从 builder 阶段把打包好的 jar 拷过来（用通配符，版本号变化也不用改这里）
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
# JAVA_OPTS 可在 docker-compose 里覆盖内存等参数
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
