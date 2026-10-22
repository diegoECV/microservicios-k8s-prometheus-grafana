# Etapa 1: Compilación (Build)
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Copiar descriptor de dependencias
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copiar código fuente y compilar JAR sin ejecutar tests para velocidad
COPY src ./src
RUN mvn clean package -DskipTests -B

# Etapa 2: Imagen final liviana de ejecución (Runtime)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Crear usuario sin privilegios para seguridad en Kubernetes
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copiar artefacto generado desde la etapa de compilación
COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
