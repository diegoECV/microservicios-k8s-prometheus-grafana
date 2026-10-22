# Informe Técnico: Microservicios Java con Monitoreo en Kubernetes

* **Institución:** Valle Grande
* **Curso:** Kubernetes con Prometheus & Grafana
* **Modalidad:** Individual
* **Dominio Elegido:** Plataforma Educativa - Catálogo de Cursos (`course-service`) y Matrículas (`enrollment-service`)
* **Autor:** Diego Alva
* **Fecha:** Octubre 2026

---

## Parte A — Conceptos Técnicos

### 1. ¿Qué es Micrometer y por qué se usa en lugar de la librería directa de Prometheus?
**Micrometer** es una fachada de métricas para aplicaciones Java (análogo a lo que SLF4J es para el logging). Proporciona una API uniforme e independiente del proveedor para recolectar métricas de rendimiento y negocio.

**Por qué se usa en lugar de la librería cliente directa de Prometheus (`simpleclient`):**
1. **Desacoplamiento e Interoperabilidad:** Permite instrumentar el código de negocio sin atarse a un motor de métricas específico. Si la organización decide cambiar o enviar métricas a Datadog, InfluxDB, Dynatrace o CloudWatch, solo se cambia la dependencia del registro (`registry`), sin modificar una sola línea de código en los servicios.
2. **Integración Nativa con Spring Boot 3:** Spring Boot Actuator utiliza Micrometer de forma predeterminada para autoinstrumentar métricas de servidor HTTP, pool de conexiones, garbage collection y JVM.
3. **Soporte de Dimensionalidad Dinámica:** Permite etiquetar métricas mediante tags (`key=value`), facilitando el agrupamiento y filtrado multidimensional en Prometheus y Grafana.

---

### 2. ¿Cuál es la diferencia entre Counter, Gauge y Timer? Da un ejemplo de cada uno tomado de tu código

| Tipo | Definición | Comportamiento | Ejemplo en nuestro Código |
| :--- | :--- | :--- | :--- |
| **Counter (Contador)** | Métrica acumulativa que representa un valor monótonamente creciente. Solo puede incrementarse o reiniciarse a cero cuando el proceso se reinicia. | Acumulativo monotónico | `enrollment_failures_total` (en `EnrollmentService.java`): Cuenta el número acumulado de matrículas fallidas por timeout o falta de vacantes. |
| **Gauge (Medidor)** | Métrica que representa un valor numérico instantáneo que puede subir y bajar libremente en cualquier momento (fotografía del momento actual). | Sube y baja | `course_available_seats_gauge` (en `CourseService.java`): Refleja la cantidad exacta de vacantes disponibles en tiempo real. Otro ejemplo clave es `course_artificial_delay_ms` que expone el delay artificial inyectado. |
| **Timer (Temporizador)** | Mide simultáneamente la duración de un evento (latencia) y la frecuencia con la que ocurre. Registra tanto el tiempo total, conteo de ejecuciones y percentiles calculados. | Conteo + Duración + Percentiles | `enrollment_processing_duration_seconds` (en `EnrollmentService.java`): Mide el tiempo total en segundos que toma procesar una matrícula completa, incluyendo la llamada HTTP con WebClient a `course-service`. |

#### Extractos de Código de Implementación:
```java
// Counter (Contador)
this.failuresCounter = Counter.builder("enrollment_failures_total")
        .description("Total de matrículas fallidas por cualquier causa")
        .tag("service", "enrollment-service")
        .register(meterRegistry);

// Gauge (Medidor)
meterRegistry.gauge("course_available_seats_gauge", this, CourseService::getTotalAvailableSeats);
meterRegistry.gauge("course_artificial_delay_ms", delayGaugeValue, AtomicLong::get);

// Timer (Temporizador)
this.processingTimer = Timer.builder("enrollment_processing_duration_seconds")
        .description("Tiempo total de procesamiento de matrículas incluyendo llamada HTTP")
        .tag("service", "enrollment-service")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry);
```

---

### 3. ¿Qué es un ServiceMonitor y cómo sabe el Prometheus Operator qué monitorear?
Un **ServiceMonitor** es un Recurso Personalizado (*Custom Resource Definition - CRD*) creado por el **Prometheus Operator**. Permite definir de manera declarativa cómo y desde dónde Prometheus debe extraer (*scrape*) métricas de un conjunto de Pods respaldados por un `Service` de Kubernetes.

**Cómo sabe qué monitorear:**
1. **Descubrimiento de Servicios por Labels (`spec.selector.matchLabels`):** El ServiceMonitor busca en el namespace aquellos `Service` de Kubernetes cuyas etiquetas coincidan exactamente con su selector (ej: `app: course-service`).
2. **Referencia de Puerto Nombrado (`spec.endpoints[].port`):** Inspecciona el Service emparejado y busca un puerto cuyo nombre coincida con el valor indicado (en nuestro proyecto, `port: http`).
3. **Ruta y Frecuencia (`path` e `interval`):** Establece la ruta HTTP del scraping (`path: /metrics`) y el intervalo de sondeo (ej. cada `5s`).
4. **Vínculo con el Operator:** A su vez, el Prometheus Operator descubre qué ServiceMonitors cargar mediante las etiquetas del CRD (`release: prometheus-stack`) o si el chart está configurado con `serviceMonitorSelector: {}`.

---

### 4. ¿Cuál es la diferencia entre liveness probe y readiness probe?

* **`livenessProbe` (Sonda de Vida):**
  * **Objetivo:** Determina si el contenedor está vivo o si ha entrado en un estado de congelamiento/bloqueo irreparable (*deadlock*).
  * **Acción si falla:** Kubernetes **reinicia el contenedor** matando el proceso del Pod para intentar recuperarlo.
  * **En nuestro proyecto:** Apunta a `GET /health`. Si el microservicio colapsa, Kubernetes lo reinicia automáticamente.

* **`readinessProbe` (Sonda de Disponibilidad):**
  * **Objetivo:** Determina si la aplicación está lista para recibir y atender tráfico entrante de los usuarios u otros servicios.
  * **Acción si falla:** Kubernetes **retira los endpoints del Pod del Service** (deja de enviarle peticiones de red), pero **NO reinicia el contenedor**. El pod permanece esperando hasta que se inicialice o se estabilice.
  * **En nuestro proyecto:** Permite que un microservicio no reciba tráfico durante el arranque hasta que Spring Boot haya cargado el ApplicationContext y las colecciones en memoria.

---

### 5. ¿Por qué es necesario apuntar Docker al daemon de Minikube antes de construir las imágenes?
Minikube corre dentro de una máquina virtual o en un contenedor aislado (en nuestro entorno, gestionado mediante Podman/Docker). Posee su propio runtime de contenedores interno y su propio almacenamiento de imágenes, completamente separado del entorno del sistema operativo anfitrión (*host*).

Si construyes las imágenes (`docker build`) en tu terminal sin redirigir el daemon:
1. Las imágenes se guardan únicamente en el almacenamiento local del host.
2. Al desplegar el Pod en Minikube con `image: course-service:1.0.0` e `imagePullPolicy: IfNotPresent`, Kubernetes buscará la imagen dentro del daemon interno de Minikube o intentará descargarla de Docker Hub público.
3. Al no encontrarla localmente ni remotamente, el pod fallará de inmediato con el error **`ErrImageNeverPull`** o **`ImagePullBackOff`**.

Al ejecutar `eval $(minikube docker-env)` o utilizar `minikube image build`, las imágenes se construyen y almacenan directamente en el motor de contenedores de Minikube, permitiendo que los Pods arranquen al instante.

---

### 6. ¿Qué ocurre si no configuras el selector de ServiceMonitors en el values.yaml del chart?
Por defecto, la instalación oficial de `kube-prometheus-stack` incluye una directiva de seguridad que hace que Prometheus **solo busque y raspe ServiceMonitors que tengan la etiqueta específica de su propio release y que estén en su mismo namespace**.

Si no se sobrescribe en `k8s/monitoring-values.yaml`:
```yaml
prometheus:
  prometheusSpec:
    serviceMonitorSelectorNilUsesHelmValues: false
    serviceMonitorSelector: {}
```
Ocurre lo siguiente:
* Prometheus Operator ignora por completo los `ServiceMonitor` creados en namespaces externos (como `valle-grande`).
* En consecuencia, los microservicios nunca aparecerán en el panel **/targets** de Prometheus.
* Prometheus no raspará ninguna métrica propia (`course_*`, `enrollment_*`), dejando los dashboards de Grafana vacíos (*No Data*).

---

## Parte B — Consultas PromQL del Sistema

### 1. Tasa de requests por minuto del Servicio B (`enrollment-service`) en los últimos 5 minutos:
```promql
rate(enrollment_requests_total[5m]) * 60
```
* **Explicación:** Calcula la tasa por segundo de solicitudes procesadas en una ventana deslizante de 5 minutos y se multiplica por 60 para obtener el valor expresado en solicitudes por minuto.

---

### 2. Latencia percentil 95 (p95) del endpoint más crítico (`/api/enrollments`):
```promql
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{uri="/api/enrollments"}[5m])) by (le))
```
* **Explicación:** Emplea los buckets de latencia expuestos por Spring Boot Actuator para calcular el percentil 95 (el 95% de las peticiones se completan en un tiempo menor o igual al valor obtenido).

---

### 3. Estado UP/DOWN de ambos microservicios al mismo tiempo:
```promql
up{job=~"course-service|enrollment-service"}
```
* **Explicación:** Retorna una serie temporal para cada servicio con valor binario `1` (si el target está UP y respondiendo al scraping) o `0` (si está DOWN/inalcanzable).

---

### 4. Consulta de Negocio: Relación de Fallos de Matrícula frente al Total de Intentos (% de Error de Negocio):
```promql
(sum(rate(enrollment_failures_total[5m])) / sum(rate(enrollment_requests_total[5m]))) * 100
```
* **Explicación:** Calcula el porcentaje de transacciones de matrícula que resultaron fallidas (por timeout de comunicación con Servicio A o por agotamiento de cupos) respecto al volumen total de solicitudes en tiempo real.

---

## Parte C — Evidencias de Casuísticas

### 🔴 Casuística 1 — El Servicio Caído (Obligatoria)

* **Contexto narrativo:** Se simula un error crítico de configuración o despliegue en `course-service` mediante una variable de entorno errónea (`SIMULATE_CRASH=true`) que induce un fallo irrecuperable en el arranque del servicio.
* **Síntoma:** El pod de `course-service` entra en estado recurrente de reinicios fallidos (`CrashLoopBackOff`), las llamadas hacia `/health` y `/metrics` dejan de responder.
* **Activación:**
  ```bash
  kubectl set env deployment/course-service SIMULATE_CRASH=true -n valle-grande
  ```
* **Diagnóstico:**
  * En Kubernetes: `kubectl get pods -n valle-grande` muestra el pod en estado `CrashLoopBackOff`.
  * En Prometheus: `/targets` marca `course-service` como **DOWN** (`up == 0`).
  * En Grafana: El panel *Estado de Salud* se torna en color **ROJO (DOWN)** y se dispara la alerta `CourseServiceDown`.
* **Resolución:**
  ```bash
  kubectl set env deployment/course-service SIMULATE_CRASH=false -n valle-grande
  kubectl rollout status deployment/course-service -n valle-grande
  ```
* **Lección técnica:** Demuestra la importancia de los *liveness probes* para aislar fallos, la diferencia con el *readiness probe*, y cómo las alertas automáticas en Prometheus permiten detectar caídas de microservicios antes de que los usuarios reporten la indisponibilidad total.

---

### 🟡 Casuística 2 — El Servicio Lento (Obligatoria)

* **Contexto narrativo:** `course-service` sufre una degradación severa de rendimiento (simulando sobrecarga de procesamiento o cuellos de botella internos). Para representarlo, se inyecta un retardo artificial de 4000 ms (4 segundos), superando el tiempo de espera límite (*timeout*) de 2500 ms configurado en el `WebClient` de `enrollment-service`.
* **Síntoma:** Los usuarios que intentan matricularse experimentan lentitud y reciben una respuesta HTTP 504 (*Gateway Timeout*) con mensaje controlado.
* **Activación:**
  ```bash
  # Activar retardo de 4000ms vía endpoint administrativo o variable de entorno:
  curl -X POST http://$(minikube ip):30081/api/courses/admin/delay \
       -H "Content-Type: application/json" -d '{"delayMs": 4000}'

  # Generar tráfico hacia enrollment-service para provocar los timeouts:
  ./load-testing/stress.sh -n 20 -m timeout
  ```
* **Diagnóstico:**
  * En Grafana: El panel *Delay Artificial Inyectado en course-service* marca `4000 ms` (color rojo).
  * En Grafana: El panel de *Latencia Promedio* se dispara por encima de los 2.5s y el panel de *Matrículas Fallidas* registra una racha de timeouts (`enrollment_timeouts_total`).
  * En Prometheus: Se activan las alertas `HighInterServiceLatency` y `HighEnrollmentFailuresRate`.
* **Resolución:**
  ```bash
  # Restaurar el delay artificial a 0 ms:
  curl -X POST http://$(minikube ip):30081/api/courses/admin/delay \
       -H "Content-Type: application/json" -d '{"delayMs": 0}'
  ```
* **Lección técnica:** En arquitecturas de microservicios, la latencia es transitiva: la lentitud en un servicio de backend degrada en cascada a todos los servicios upstream. Sin observabilidad con métricas Gauge de latencia y Timers en ambos lados, es imposible aislar con certeza qué servicio es el causante de la degradación.

---

### 🟠 Casuística 3 — Agotamiento Masivo de Vacantes de Negocio (Libre)

* **Contexto narrativo:** Campaña intensiva de inscripciones de fin de ciclo donde la alta demanda de estudiantes agota todas las vacantes del curso más popular (`CRS-004`). Nuevos intentos de inscripción son rechazados por la regla de negocio.
* **Síntoma:** Los estudiantes reciben respuestas HTTP 400 (*Bad Request*) con el mensaje *"El curso no dispone de vacantes para confirmar la matrícula"*, imposibilitando nuevas ventas.
* **Activación:**
  ```bash
  # Enviar ráfaga de matrículas al curso CRS-004 que solo tiene 10 vacantes disponibles:
  ./load-testing/stress.sh -n 15 -i 0.1 -m exhaust
  ```
* **Diagnóstico:**
  * En Grafana: El panel Gauge *Vacantes Disponibles en Catálogo* cae a niveles críticos (<= 5).
  * En Grafana: La serie temporal de *Matrículas Fallidas / Rechazadas* sube abruptamente.
  * En Prometheus: Se dispara la alerta de negocio `CourseSeatsExhaustedCritical`.
* **Resolución:**
  ```bash
  # Crear un nuevo curso o ampliar el inventario de cursos disponibles:
  curl -X POST http://$(minikube ip):30081/api/courses \
       -H "Content-Type: application/json" \
       -d '{"id":"CRS-005","title":"Arquitectura Cloud Native Avanzada","instructor":"Ing. Especialista","category":"DevOps","availableSeats":30,"totalSeats":30,"price":200.0}'
  ```
* **Lección técnica:** El monitoreo no debe limitarse a la infraestructura técnica (CPU, RAM, HTTP 500); las métricas de dominio de negocio son indispensables para anticipar pérdidas comerciales y tomar acciones operativas oportunas (ej. reapertura de cupos).

---

## Parte D — Evidencias del Sistema

### 1. Captura de `/targets` en Prometheus (ambos servicios en estado UP)
> *(Insertar aquí captura de pantalla de `http://<MINIKUBE_IP>:30090/targets` mostrando los jobs `course-service` y `enrollment-service` con estado 1/1 UP)*

---

### 2. Captura del Dashboard de Grafana con los Seis Paneles Funcionando
> *(Insertar aquí captura de pantalla del dashboard importado en Grafana con los 6 paneles visualizando métricas en tiempo real)*

---

### 3. Captura de `kubectl get pods` mostrando todos los pods en Running
> *(Insertar aquí captura de la terminal ejecutando `kubectl get pods -n valle-grande -o wide` y `kubectl get pods -n monitoring`)*

---

### 4. Captura de al menos una alerta en estado FIRING durante alguna casuística
> *(Insertar aquí captura de la pestaña Alerts en Prometheus `http://<MINIKUBE_IP>:30090/alerts` mostrando la alerta `CourseServiceDown` o `HighEnrollmentFailuresRate` en estado **FIRING**)*
