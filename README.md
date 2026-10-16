# AAPRC · Administración de Asignaciones para las Reuniones Cristianas

La versión 1.8.4 incorpora el reporte mensual de Asignaciones Mecánicas. Mantiene el identificador estable `mx.escuelaministerio.app.estable`, por lo que conservará la base de datos al actualizar una versión estable anterior.

Aplicación Android modular para administrar asignaciones de las reuniones cristianas.

## Página de inicio

- Menú general AAPRC con los módulos Escuela del Ministerio Teocrático, Vida y Ministerio, Reunión Pública y Atalaya, Asignaciones Mecánicas y Predicación.
- Portada azul oscuro con el nuevo logotipo AAPRC en PNG transparente.
- Escuela del Ministerio Teocrático, Vida y Ministerio, Reunión Pública y Atalaya y Asignaciones Mecánicas son módulos activos.
- El encabezado de Escuela del Ministerio Teocrático utiliza nuevamente el icono original de la aplicación.
- Predicación abre la Campaña de Predicación Pública, Grupos de Predicación y el Programa de Transporte; las otras tres partes permanecen pendientes de definición.
- El botón Inicio y el botón Atrás del dispositivo regresan al menú general desde el módulo activo.
- Todas las ventanas AlertDialog usan un constructor común que impide cerrarlas por toques exteriores o con Atrás; requieren los botones de Guardar, Aceptar, Cancelar o una elección explícita. La ventana Editar/Registrar Asignaciones Mecánicas también desactiva el cierre externo explícitamente.
- La base de datos compartida amplía el catálogo de alumnos con 15 indicadores de participación para los módulos futuros y un número de Grupo.
- La migración conserva todos los alumnos existentes; sus nuevas clasificaciones quedan desactivadas y Grupo queda sin asignar hasta que el usuario los edite.
- La importación Excel anterior conserva las clasificaciones nuevas cuando actualiza alumnos. La plantilla ampliada se incorporará en una actualización posterior.

## Módulo SOPORTE

- Acceso oculto al tocar el logotipo AAPRC de la portada, con usuario y contraseña; al volver a Inicio se cierra la sesión.
- Primer acceso: `admin` y `root`. Obliga a elegir una nueva contraseña de ocho caracteres o más antes de mostrar SOPORTE; `root` deja de ser válida y no puede volver a establecerse.
- Usuarios y contraseñas se almacenan dentro de la base de datos, con sal aleatoria y PBKDF2-SHA256; los usuarios adicionales cambian la contraseña temporal en su primer ingreso. Se pueden dar de alta usuarios, modificar sus nombres y restablecer sus contraseñas desde SOPORTE, validando la contraseña de la persona conectada. Tras cinco intentos incorrectos hay una espera de un minuto.
- Respaldo cifrado AES-GCM de todas las tablas SQLite, con clave del archivo elegida y confirmada por el usuario; Android ofrece elegir dónde guardarlo. La clave del respaldo no se almacena en la aplicación.
- Restauración desde archivos cifrados `.aaprc`, bases SQLite `.db` anteriores o respaldos internos. Verifica integridad, esquema y versión antes de reemplazar el archivo, crea una copia interna previa y conserva los usuarios actuales de SOPORTE para que un respaldo viejo nunca reactive `admin/root`.
- Comprobación no destructiva de integridad, conteos por tabla y algunas referencias de estudiantes y presentaciones; reconstrucción de avisos pendientes de Escuela y Vida y Ministerio tras una restauración o a petición del usuario.
- Los colores y la semana activa son preferencias externas a la base: los colores permanecen en el dispositivo y la semana activa se limpia al restaurar. El archivo exportado contiene la base de datos completa, no estas preferencias.

## Módulo Predicación

- Configuración separada de la captura: fechas inicial y final inclusivas, hora inicial y final de la jornada, módulo, congregación y uno a seis exhibidores; calcula turnos completos de dos horas. Advierte si la jornada no es divisible entre dos horas.
- Captura posterior por día, turno y exhibidor: cuatro plazas de publicadores elegidos del catálogo compartido. Los publicadores pueden repetir en otro turno, pero no simultáneamente en dos exhibidores.
- Al completar todas las plazas del día, «Finalizar día» elige una sola vez al capitán diario entre los publicadores de esa jornada y a dos transportistas distintos, uno para llevar y otro para recoger los exhibidores. Modificar una plaza ya capturada devuelve el día al estado pendiente hasta que se confirme de nuevo.
- JPG semanal con los días como columnas y secciones por exhibidor; JPG de cada exhibidor y turno con el mismo capitán del día y los transportistas para WhatsApp Business, disponible al finalizar el día.
- La migración conserva las asignaciones anteriores y los capitanes históricos por turno. Si todos coincidían en un mismo día, se propone ese capitán como borrador; si no coinciden, se pide elegir uno y se conservan los registros antiguos. SOPORTE comprueba y respalda las nuevas tablas. No se modificaron las clasificaciones de alumnos.
- Programa semanal de la congregación, visita de circuito y limpieza del Salón del Reino se definirán posteriormente.

## Grupos de Predicación

- Catálogo de grupos identificado por número y nombre, conectado al campo Grupo que ya existe en cada alumno.
- Cada grupo registra un capitán y un auxiliar diferentes, seleccionados entre sus integrantes activos.
- Punto de encuentro con nombre y dirección obligatorios, además de teléfono o celular opcional.
- Administración conjunta de integrantes: permite incorporar alumnos sin grupo o trasladarlos desde otro grupo sin duplicarlos.
- Los Precursores Regulares se identifican visualmente en la lista de integrantes y se contabilizan en el resumen de cada grupo.
- Al actualizar desde una base anterior, los números de grupo ya capturados generan automáticamente un grupo provisional para conservar la clasificación existente.
- Al eliminar un grupo no se eliminan alumnos: sus integrantes quedan disponibles como alumnos sin grupo.
- SOPORTE respalda la nueva tabla y verifica grupos faltantes, responsables fuera de su grupo y responsables duplicados.

## Programa de Transporte

- Cada viaje registra fecha, hora, motivo y lugar de salida obligatorio, además de la cantidad de autobuses y la capacidad individual de cada uno.
- Primero se forma una lista global de pasajeros. Pueden seleccionarse alumnos activos o personas interesadas; estas últimas se registran únicamente con su nombre y también pueden darse de alta durante la captura del viaje.
- El registro se cierra automáticamente al completar la capacidad total o manualmente cuando se decide terminar la lista. Solo entonces se habilita la asignación de asientos.
- La cantidad y capacidad de autobuses pueden corregirse mientras todavía no haya pasajeros registrados.
- Cada pasajero ocupa un único asiento. El capitán de cada autobús se elige exclusivamente entre los pasajeros que ya tienen asiento en ese mismo autobús y ocupa ese asiento como cualquier otro pasajero.
- Si se cambia o libera el asiento del capitán, su designación se elimina para evitar que el programa conserve una capitanía inválida. Para finalizar se exige que todos los pasajeros tengan asiento y que cada autobús tenga capitán.
- El reporte final reproduce el mapa de asientos por autobús: genera un PDF imprimible, con una página para cada autobús, y un JPG individual para compartir mediante WhatsApp Business.
- Historial recíproco: permite consultar cada viaje con sus autobuses, capitanes, asientos y pasajeros, y también elegir un pasajero para ver todos sus viajes, autobús, asiento y las ocasiones en que fue capitán.
- SOPORTE incluye las nuevas tablas en el respaldo, la restauración, el conteo y las comprobaciones de integridad de la base compartida.

## Módulo Vida y Ministerio

- Programa semanal independiente, siempre para Sala Principal.
- Usa las mismas semanas y los mismos presidentes configurados en Escuela del Ministerio Teocrático.
- Las semanas de visita se trasladan automáticamente del miércoles al martes en ambos módulos.
- Plantilla Excel separada con Fecha, Número de asignación, Nombre de la asignación, Tiempo y Tema.
- La reimportación actualiza por fecha y número, sin duplicar el programa.
- Solo pueden asignarse alumnos activos con la clasificación Vida y Ministerio.
- Estudio del Libro exige además un conductor habilitado para Estudio del Libro y un lector habilitado para Lector del Estudio del Libro.
- Una persona puede tener varias asignaciones el mismo día; dentro de un Estudio del Libro, conductor y lector deben ser personas distintas.
- En semanas de visita, la configuración solicita nombre del superintendente de circuito, tema del discurso de servicio y canción; el Estudio del Libro se omite del programa, PDF y avisos, conservando sus datos si la semana se corrige.
- El lector del Estudio del Libro se elige por su propia clasificación, aunque no tenga activada Vida y Ministerio. En la tarjeta individual del estudio, la función del encargado figura como «Conductor» y la del lector como «Lector del Estudio del Libro».

## Módulo Asignaciones Mecánicas

- Registros independientes «entre semana» y «fin de semana» por fecha: los anteriores se conservan como «entre semana» y pueden coexistir con una reunión pública celebrada el mismo día. La visita entre semana mantiene su traslado al martes.
- Nueve funciones: acomodador de entrada, acomodadores de auditorio 1 y 2, micrófonos 1 y 2, oraciones inicial y final, y plataforma 1 y 2. Solo se pueden seleccionar varones activos con autorización para la función correspondiente en el catálogo de alumnos.
- Tres selectores de canciones (inicial, intermedia y final), con número y título de la tabla CANCIONES; se permite guardar el registro parcialmente para completarlo después. Los valores previamente escritos a mano se conservan al editar.
- Se puede editar o eliminar solo el registro mecánico de una fecha. Al borrar una semana completa también se borra ese registro.
- «Imagen del programa entre semana» genera y comparte un JPG por fecha con Vida y Ministerio, Escuela y las mecánicas de ese día; la imagen ajusta su altura al contenido.
- «Imagen del programa de fin de semana» genera y comparte un JPG independiente con presidente, canciones, oraciones, Discurso Público, Estudio de la Atalaya, Discurso Final durante la visita y las mecánicas registradas como «WEEKEND».
- «PDF mensual de asignaciones» genera una sola hoja tamaño carta horizontal para el mes seleccionado, con oración inicial, presidente, lector, oración final, acomodadores, micrófonos y limpieza. El lector procede del Estudio del Libro entre semana y de la Atalaya el fin de semana; todos los títulos y datos se imprimen en mayúsculas.
- La limpieza rota automáticamente entre cinco grupos por cada reunión celebrada. El punto de partida es GRUPO 2 el miércoles 7 de octubre de 2026 y la secuencia continúa 3, 4, 5, 1; las reuniones suspendidas se muestran en una columna combinada y no consumen turno.
- El encabezado del reporte permite capturar congregación, localidad y circuito, y conserva los últimos valores utilizados.

## Módulo Reunión Pública y Atalaya

- Configuración semanal por selector de fecha, habitualmente domingo pero configurable para otro día. El presidente se selecciona entre los alumnos varones activos de la base compartida. Se puede marcar visita o asamblea (reunión suspendida).
- Discurso Público: orador local activo autorizado en «Discurso Público» u orador del nuevo catálogo de visitantes (nombre, congregación, localidad y nombramiento); número y título de bosquejo.
- Estudio de la Atalaya: conductor y lector elegidos de forma independiente entre varones activos autorizados en «Atalaya» y «Lector de la Atalaya»; tema y prohibición de seleccionar la misma persona en ambos puestos.
- Durante la visita el visitante seleccionado es el superintendente de circuito que presenta tanto el Discurso Público como el Discurso Final. Se registra el título del Discurso Final y se cancela el lector de la Atalaya.
- Historial de oradores locales y visitantes por fecha, bosquejo y evaluación básica del orador; historial independiente de lectores. Las semanas suspendidas no cuentan como participaciones.
- Asignaciones Mecánicas dispone de un registro de fin de semana asociado a cada reunión, separado del registro entre semana; eliminar la reunión elimina solo sus mecánicas de fin de semana.
- El guardado de responsables mecánicos del fin de semana valida exclusivamente la Reunión Pública y Atalaya correspondiente y no depende del programa de Escuela.
- Evaluación con cronómetro, asistencia, sustituto, asistencia del lector, apego al tema y comentarios.
- Historial con última participación, asignaciones futuras y detalle de evaluación.
- PDF mensual, PDF semanal, cuatro avisos individuales por hoja e imágenes JPG para WhatsApp.
- Recordatorios locales siete días antes para participante y lector, incluyendo el presidente a quien deben reportar incidencias.

## Funciones incluidas

- Catálogos editables de alumnos, presentaciones y presidentes.
- Importación desde un libro XLSX con las hojas Alumnos, Presidentes y Presentaciones.
- Las presentaciones se identifican por fecha y número para evitar que un ID reutilizado sustituya otra semana.
- Las filas consecutivas pueden dejar vacía la fecha: se utiliza la fecha anterior y se avisa si una semana queda con menos de cuatro presentaciones.
- Plantilla Excel incluida y compartible desde la aplicación.
- Los registros con un ID existente se actualizan; alumnos y presidentes también se reconocen por nombre normalizado y celular para impedir duplicados al volver a importar el libro.
- La herramienta “Revisar duplicados” crea un respaldo, unifica solo coincidencias exactas y conserva sus asignaciones y presidencias; los casos ambiguos quedan para revisión manual.
- Importación extraordinaria del libro matricial HISTORICO: verde identifica al estudiante, azul al ayudante y amarillo al estudiante durante una visita.
- La importación histórica solicita el año, presenta una vista previa, respalda la base antes de escribir y evita duplicar movimientos al cargar nuevamente el archivo.
- Los movimientos históricos se muestran en la actividad del alumno y participan en el cálculo de sugerencias de ayudantes, sin agregarse al programa mensual cotidiano.
- Los IDs repetidos de presentaciones se detectan y esas filas se recuperan por fecha y número para no perder semanas del programa.
- El catálogo y la plantilla usan únicamente “Fuente de información”; “Material” se conserva solo para migrar datos antiguos.
- El PDF mensual ajusta el texto dentro de cada celda y evita superposiciones o desbordes.
- Calificador del alumno de 0 a 10; el valor 0 suspende al alumno.
- Validación de parejas: si el nivel es 5 o menor, no se aceptan dos alumnos con el mismo calificador.
- Programación manual para Sala Principal y Sala Auxiliar.
- Sugerencias de ayudantes que priorizan parejas nuevas, indican los días desde la última asignación y muestran la fecha de la última vez que trabajaron juntos.
- Una persona no puede participar en ambas salas en la misma presentación, aunque sí puede recibir otra presentación el mismo día.
- Selectores de calendario para las fechas de reuniones y presentaciones.
- Selector de mes y año en la página principal para elegir el programa que se va a trabajar.
- La Lectura de la Biblia se asigna únicamente a hombres activos.
- La Lectura de la Biblia no muestra ni solicita ayudante.
- “¿Qué diría?” utiliza los mismos controles individuales: solo hombres activos y sin ayudante.
- Las presentaciones cuyo marco es “Discurso” también se tratan como asignaciones individuales para hombres y sin ayudante.
- La semana configurada queda activa y se usa automáticamente al capturar nuevas presentaciones.
- Después de configurar una semana, el programa muestra únicamente sus asignaciones; “Ver mes completo” restaura la vista mensual sin cambiar la semana activa.
- “Eliminar semana” borra definitivamente su configuración, presentaciones, asignaciones y evaluaciones; también cancela sus recordatorios y la excluye del PDF mensual.
- Reporte PDF de una semana y una sala seleccionadas, con todas sus presentaciones y participantes.
- Al marcar una visita, las presentaciones capturadas para el miércoles se trasladan al martes de esa misma semana y se conserva únicamente la Sala Principal.
- Evaluación semanal con cronómetro, asistencia del estudiante y ayudante, sustituto, apego al material, cumplimiento de la lección y comentarios.
- Tres reuniones suspendidas y dos visitas como máximo por año.
- En una visita se bloquea automáticamente la Sala Auxiliar.
- Presidente semanal elegido de un catálogo independiente.
- Historial por alumno, sala, función, compañero y días transcurridos.
- El historial usa una fecha de consulta para calcular la última participación y las asignaciones futuras; cada registro abre el detalle de su evaluación tanto para maestros como para ayudantes.
- Programa mensual en PDF, una página tamaño carta horizontal.
- Hojas de asignación en PDF tamaño carta, cuatro formatos por página con líneas de corte.
- Imagen JPG de cada asignación para compartir por WhatsApp.
- Recordatorio local siete días antes, con el nombre del presidente a quien reportar incidencias.
- Paleta visual configurable para banners, cabeceras y textos.

## WhatsApp Business

La primera versión genera el JPG, prepara el mensaje y abre WhatsApp. El envío completamente automático requiere una cuenta de Meta Business, un número habilitado en WhatsApp Business Platform, una plantilla aprobada, consentimiento de los destinatarios y un servicio seguro que custodie el token de acceso. Esos datos no deben guardarse dentro del APK.

## Compilación

Requisitos: JDK 17, Android SDK 36, Build Tools 35 o superior y Gradle 8.13.

```bash
gradle :app:assembleDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.


## Predicación · Campañas — versión 1.8.4

- Se elimina la opción **Finalizar día**.
- Cada módulo permite hasta cuatro publicadores; con dos publicadores el módulo se considera viable.
- El capitán se asigna por jornada cuando todos los módulos de esa jornada son viables.
- El transporte de campañas es independiente del módulo general de Transporte: se define un único publicador encargado de trasladar los módulos tanto al inicio como al final de la jornada.
- Los reportes de campaña ya no dependen de marcar la jornada como finalizada.
